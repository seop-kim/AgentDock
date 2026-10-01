package com.agent.dock.execution.service;

import com.agent.dock.agent.domain.Agent;
import com.agent.dock.agent.repository.AgentRepository;
import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.domain.ExecutionDecision;
import com.agent.dock.execution.domain.ExecutionStatus;
import com.agent.dock.execution.dto.ExecutionFinishedEvent;
import com.agent.dock.execution.dto.ExecutionLogResponse;
import com.agent.dock.execution.dto.ExecutionResponse;
import com.agent.dock.execution.dto.ExecutionTreeResponse;
import com.agent.dock.execution.repository.ExecutionLogRepository;
import com.agent.dock.execution.repository.ExecutionRepository;
import com.agent.dock.runtime.dto.AgentExecutionResult;
import com.agent.dock.runtime.dto.ExecutionMetrics;
import com.agent.dock.runtime.interfaces.AgentRuntime;
import com.agent.dock.runtime.service.RuntimeRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 실행의 생성·조회·취소와 상태/결과 저장을 맡는다.
 * 스텝을 실제로 돌리는 일은 {@link ExecutionRunner}, 위임 루프는 delegation 패키지가 맡는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionService {
    private final ExecutionRepository executionRepository;
    private final ExecutionLogRepository logRepository;
    private final AgentRepository agentRepository;
    private final ExecutionGuard guard;
    private final ExecutionFactory factory;
    private final ExecutionRunner runner;
    private final ExecutionStreamHub streamHub;
    private final RuntimeRegistry runtimeRegistry;
    private final ApplicationEventPublisher eventPublisher;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    public ExecutionResponse findOne(Long id) {
        Execution execution = executionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Execution %d not found".formatted(id)));
        return ExecutionResponse.from(execution);
    }

    public List<ExecutionLogResponse> getLogs(Long executionId) {
        return logRepository.findByExecutionIdOrderByCreatedAtAsc(executionId).stream()
                .map(ExecutionLogResponse::from).toList();
    }

    /** 실행 트리 조회: 루트부터 순서대로 평평하게(깊이 포함) 돌려준다. 가운데 실행을 줘도 루트를 찾아 올린다. */
    public ExecutionTreeResponse getTree(Long executionId) {
        Execution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new NotFoundException("Execution %d not found".formatted(executionId)));
        Execution root = execution.getRootExecutionId() == null
                ? execution
                : executionRepository.findById(execution.getRootExecutionId()).orElse(execution);

        List<Execution> all = new ArrayList<>();
        all.add(root);
        all.addAll(executionRepository.findByRootExecutionIdOrderByIdAsc(root.getId()));

        // 자식은 항상 부모보다 나중에 만들어져 id 가 크다. 그래서 부모 깊이는 이미 계산돼 있다.
        Map<Long, Integer> depths = new HashMap<>();
        List<ExecutionTreeResponse.ExecutionTreeNode> nodes = new ArrayList<>();
        for (Execution node : all) {
            Long parentId = node.getParentExecutionId();
            int depth = parentId == null ? 0 : depths.getOrDefault(parentId, 0) + 1;
            depths.put(node.getId(), depth);
            nodes.add(new ExecutionTreeResponse.ExecutionTreeNode(
                    ExecutionResponse.from(node),
                    depth,
                    agentName(node.getAgentId()),
                    node.getDelegatedTargetAgentId() == null ? null : agentName(node.getDelegatedTargetAgentId()),
                    logRepository.countByExecutionId(node.getId())));
        }
        return new ExecutionTreeResponse(root.getId(), nodes);
    }

    private String agentName(Long agentId) {
        if (agentId == null) {
            return null;
        }
        return agentRepository.findById(agentId).map(Agent::getName).orElse(null);
    }

    public ExecutionResponse create(Long agentId, Long projectId, String prompt) {
        return create(agentId, projectId, prompt, null);
    }

    public ExecutionResponse create(Long agentId, Long projectId, String prompt, Long taskId) {
        ExecutionGuard.Target target = guard.prepare(agentId, projectId, null);
        Execution execution = factory.createRoot(target, prompt, taskId);
        // 응답을 돌려준 뒤 바로 구독할 수 있도록 로그 버퍼를 먼저 연다.
        streamHub.open(execution.getId());
        executor.submit(() -> runSingle(execution.getId(), target, prompt, taskId));
        return ExecutionResponse.from(execution);
    }

    /** 위임 실행의 루트 실행을 만든다(스텝은 오케스트레이터가 돌린다). */
    public Started startRoot(Long agentId, Long projectId, Long groupId, String prompt, Long taskId) {
        ExecutionGuard.Target target = guard.prepare(agentId, projectId, groupId);
        return new Started(factory.createRoot(target, prompt, taskId), target);
    }

    public record Started(Execution execution, ExecutionGuard.Target target) {
    }

    public void markRunning(Long executionId) {
        update(executionId, execution -> {
            execution.setStatus(ExecutionStatus.RUNNING);
            if (execution.getStartedAt() == null) {
                execution.setStartedAt(Instant.now());
            }
            // 판단이 여러 스텝으로 이어지는 실행은 한 스텝이 끝나도 아직 끝난 것이 아니다.
            execution.setFinishedAt(null);
        });
    }

    /** 판단은 끝났지만 결과를 사람에게 넘겨야 하는 상태(계약 실패·맡길 대상 없음 등). */
    public void markEscalated(Long executionId, String reason) {
        update(executionId, execution -> {
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setFinishedAt(Instant.now());
            execution.setErrorMessage(reason);
        });
        streamHub.system(executionId, "⎿ " + reason);
    }

    /** 판단을 마쳤다(성공). 여러 스텝을 돌린 실행의 마지막에 부른다. */
    public void markSucceeded(Long executionId) {
        update(executionId, execution -> {
            execution.setStatus(ExecutionStatus.SUCCEEDED);
            execution.setFinishedAt(Instant.now());
        });
    }

    public void markWaitingChild(Long executionId) {
        update(executionId, execution -> execution.setStatus(ExecutionStatus.WAITING_CHILD));
    }

    public void markFailed(Long executionId, Throwable cause) {
        update(executionId, execution -> {
            execution.setStatus(ExecutionStatus.FAILED);
            execution.setFinishedAt(Instant.now());
            execution.setErrorMessage(String.valueOf(cause));
        });
    }

    /** 위임 판단 결과(계약)를 남긴다. 위임한 실행은 DELEGATE 로 남고 완료로 덮지 않는다. */
    public void recordDecision(Long executionId, ExecutionDecision decision, Long targetAgentId) {
        update(executionId, execution -> {
            execution.setDecision(decision);
            if (targetAgentId != null) {
                execution.setDelegatedTargetAgent(agentRepository.getReferenceById(targetAgentId));
            }
        });
    }

    /** 다음 실행으로 넘길 Handoff(요약·변경 파일). */
    public void recordHandoff(Long executionId, Map<String, Object> handoff) {
        update(executionId, execution -> execution.setHandoff(handoff));
    }

    /** 한 스텝의 결과(텍스트·계측값)를 저장하고 최종 상태를 돌려준다. */
    public ExecutionStatus applyResult(Long executionId, AgentExecutionResult result) {
        ExecutionStatus status = result.succeeded() ? ExecutionStatus.SUCCEEDED : ExecutionStatus.FAILED;
        update(executionId, execution -> {
            ExecutionMetrics metrics = result.metrics();
            execution.setResultText(result.resultText());
            execution.setExitCode(result.exitCode());
            execution.setStatus(status);
            execution.setFinishedAt(Instant.now());
            if (metrics != null) {
                execution.setInputTokens(metrics.inputTokens());
                execution.setOutputTokens(metrics.outputTokens());
                execution.setCacheReadTokens(metrics.cacheReadTokens());
                execution.setCacheCreationTokens(metrics.cacheCreationTokens());
                execution.setCostUsd(metrics.costUsd());
                execution.setDurationMs(metrics.durationMs());
                execution.setNumTurns(metrics.numTurns());
                execution.setSessionId(metrics.sessionId());
            }
            if (!result.succeeded() && result.exitCode() != 0) {
                execution.setErrorMessage("exit code %d".formatted(result.exitCode()));
            }
        });
        return status;
    }

    /** Task 상태를 갱신하도록 알린다(위임 실행은 트리가 전부 끝났을 때 한 번만 부른다). */
    public void publishFinished(Long executionId, Long taskId, ExecutionStatus status) {
        if (taskId == null) {
            return;
        }
        eventPublisher.publishEvent(new ExecutionFinishedEvent(executionId, taskId, status));
    }

    public SseEmitter streamLogs(String executionId) {
        return streamHub.subscribe(Long.parseLong(executionId));
    }

    public Map<String, Boolean> cancel(Long id) {
        Execution execution = executionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Execution %d not found".formatted(id)));
        Agent agent = agentRepository.findByIdWithRelations(execution.getAgentId())
                .orElseThrow(() -> new NotFoundException("Agent %d not found".formatted(execution.getAgentId())));
        AgentRuntime runtime = runtimeRegistry.resolve(agent.getProvider().getKey().name());
        runtime.cancel(String.valueOf(execution.getId()));
        return Map.of("cancelled", true);
    }

    private void runSingle(Long executionId, ExecutionGuard.Target target, String prompt, Long taskId) {
        ExecutionStatus status = ExecutionStatus.FAILED;
        Integer exitCode = null;
        try {
            markRunning(executionId);
            Agent agent = agentRepository.findByIdWithRelations(target.agentId()).orElseThrow();
            AgentExecutionResult result = runner.runStep(executionId, agent, target.workspacePath(),
                    target.systemPrompt(), prompt, null);
            status = applyResult(executionId, result);
            exitCode = result.exitCode();
            publishFinished(executionId, taskId, status);
        } catch (Exception ex) {
            log.error("execution {} failed", executionId, ex);
            markFailed(executionId, ex);
            publishFinished(executionId, taskId, ExecutionStatus.FAILED);
        } finally {
            streamHub.close(executionId, status, exitCode);
        }
    }

    private void update(Long executionId, Consumer<Execution> change) {
        Execution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new NotFoundException("Execution %d not found".formatted(executionId)));
        change.accept(execution);
        executionRepository.save(execution);
    }
}
