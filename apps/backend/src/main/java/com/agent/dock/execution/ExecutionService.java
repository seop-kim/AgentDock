package com.agent.dock.execution;

import com.agent.dock.agent.Agent;
import com.agent.dock.agent.AgentRepository;
import com.agent.dock.common.ForbiddenException;
import com.agent.dock.common.NotFoundException;
import com.agent.dock.permission.PermissionAction;
import com.agent.dock.permission.PermissionService;
import com.agent.dock.project.Project;
import com.agent.dock.project.ProjectRepository;
import com.agent.dock.runtime.AgentExecutionRequest;
import com.agent.dock.runtime.AgentRuntime;
import com.agent.dock.runtime.RuntimeRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionService {
    private final ExecutionRepository executionRepository;
    private final ExecutionLogRepository logRepository;
    private final AgentRepository agentRepository;
    private final ProjectRepository projectRepository;
    private final RuntimeRegistry runtimeRegistry;
    private final PermissionService permissionService;
    private final ApplicationEventPublisher eventPublisher;

    private final Map<String, List<SseEmitter>> streams = new ConcurrentHashMap<>();
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

    public ExecutionResponse create(Long agentId, Long projectId, String prompt) {
        return create(agentId, projectId, prompt, null);
    }

    public ExecutionResponse create(Long agentId, Long projectId, String prompt, Long taskId) {
        Agent agent = agentRepository.findByIdWithRelations(agentId)
                .orElseThrow(() -> new NotFoundException("Agent %d not found".formatted(agentId)));
        // 작업 디렉터리는 프로젝트가 결정한다 (워크스페이스는 프로젝트에 귀속)
        Project project = projectRepository.findWithWorkspace(projectId)
                .orElseThrow(() -> new NotFoundException("Project %d not found".formatted(projectId)));

        // Permission Enforcement: Prompt 설명이 아니라 실행 전 Backend에서 실제로 차단한다.
        if (!permissionService.isAllowed(agent.getPermissionProfile(), PermissionAction.TERMINAL_EXECUTE)) {
            throw new ForbiddenException("Agent permission profile does not allow TERMINAL_EXECUTE");
        }

        Execution execution = new Execution();
        execution.setAgent(agent);
        execution.setWorkspace(project.getWorkspace());
        execution.setTaskId(taskId);
        execution.setPrompt(prompt);
        execution.setStatus(ExecutionStatus.PENDING);
        Execution saved = executionRepository.save(execution);

        executor.submit(() -> run(saved.getId(), agent, project.getWorkspace().getPath(), prompt, taskId));

        return ExecutionResponse.from(saved);
    }

    public SseEmitter streamLogs(String executionId) {
        List<SseEmitter> emitters = streams.get(executionId);
        SseEmitter emitter = new SseEmitter(0L);
        if (emitters == null) {
            emitter.complete();
            return emitter;
        }
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        return emitter;
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

    private void run(Long id, Agent agent, String workspacePath, String prompt, Long taskId) {
        String streamKey = String.valueOf(id);
        streams.put(streamKey, new CopyOnWriteArrayList<>());

        updateStatus(id, ExecutionStatus.RUNNING, Instant.now(), null, null, null);

        try {
            AgentRuntime runtime = runtimeRegistry.resolve(agent.getProvider().getKey().name());
            var result = runtime.execute(new AgentExecutionRequest(
                    streamKey, prompt, workspacePath, agent.getModel(), agent.getMode(),
                    (chunk, stream) -> {
                        broadcast(streamKey, stream, chunk);
                        persistLog(id, stream, chunk);
                    }
            ));
            ExecutionStatus finalStatus = result.exitCode() == 0 ? ExecutionStatus.SUCCEEDED : ExecutionStatus.FAILED;
            updateStatus(id, finalStatus, null, Instant.now(), result.exitCode(), null);
            publishFinished(id, taskId, finalStatus);
        } catch (Exception ex) {
            log.error("execution {} failed", id, ex);
            updateStatus(id, ExecutionStatus.FAILED, null, Instant.now(), null, String.valueOf(ex));
            publishFinished(id, taskId, ExecutionStatus.FAILED);
        } finally {
            List<SseEmitter> emitters = streams.remove(streamKey);
            if (emitters != null) {
                emitters.forEach(SseEmitter::complete);
            }
        }
    }

    private void updateStatus(Long id, ExecutionStatus status, Instant startedAt, Instant finishedAt, Integer exitCode, String errorMessage) {
        Execution execution = executionRepository.findById(id).orElseThrow();
        execution.setStatus(status);
        if (startedAt != null) execution.setStartedAt(startedAt);
        if (finishedAt != null) execution.setFinishedAt(finishedAt);
        if (exitCode != null) execution.setExitCode(exitCode);
        if (errorMessage != null) execution.setErrorMessage(errorMessage);
        executionRepository.save(execution);
    }

    private void publishFinished(Long executionId, Long taskId, ExecutionStatus status) {
        if (taskId == null) {
            return;
        }
        eventPublisher.publishEvent(new ExecutionFinishedEvent(executionId, taskId, status));
    }

    private void broadcast(String streamKey, String stream, String content) {
        List<SseEmitter> emitters = streams.get(streamKey);
        if (emitters == null) return;
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().data(Map.of("stream", stream, "content", content)));
            } catch (IOException ex) {
                emitters.remove(emitter);
            }
        }
    }

    private void persistLog(Long executionId, String stream, String content) {
        ExecutionLog logEntry = new ExecutionLog();
        logEntry.setExecution(executionRepository.getReferenceById(executionId));
        logEntry.setStream(stream.equalsIgnoreCase("stderr") ? LogStream.STDERR : LogStream.STDOUT);
        logEntry.setContent(content);
        logRepository.save(logEntry);
    }
}
