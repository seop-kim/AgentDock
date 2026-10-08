package com.agent.dock.execution.service;

import com.agent.dock.agent.domain.Agent;
import com.agent.dock.agent.repository.AgentRepository;
import com.agent.dock.common.exception.ConflictException;
import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.execution.domain.ChangedFile;
import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.domain.ExecutionDecision;
import com.agent.dock.execution.domain.ExecutionStatus;
import com.agent.dock.execution.domain.MergeStatus;
import com.agent.dock.execution.dto.ExecutionFinishedEvent;
import com.agent.dock.execution.dto.ExecutionLogResponse;
import com.agent.dock.execution.dto.ExecutionResponse;
import com.agent.dock.execution.dto.ExecutionTreeResponse;
import com.agent.dock.execution.repository.ExecutionLogRepository;
import com.agent.dock.execution.repository.ExecutionRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.runtime.dto.AgentExecutionResult;
import com.agent.dock.runtime.dto.ExecutionMetrics;
import com.agent.dock.runtime.interfaces.AgentRuntime;
import com.agent.dock.runtime.service.RuntimeRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
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
    private final ExecutionStreamHub streamHub;
    private final RuntimeRegistry runtimeRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final WorktreeService worktreeService;
    /** 전역 SSE 스트림에 "실행이 바뀌었다"를 알린다(화면은 이 알림을 받고 바뀐 조각만 다시 읽는다). */
    private final EventPublisher changeEvents;

    /** 서버가 죽었다 다시 떠 고아로 남은 실행에 남기는 사유. */
    private static final String STALE_EXECUTION_REASON = "서버 재기동으로 중단됨";
    /** 사람이 화면에서 취소한 실행에 남기는 사유. */
    private static final String MANUAL_CANCEL_REASON = "사용자가 취소함";

    /** 사람의 답을 기다리다 만료된 실행의 사유(방치된 트리가 영원히 멈춰 있지 않게). */
    private static final String WAITING_INPUT_EXPIRED_REASON = "답이 없어 만료됨";

    /** 입력 대기 만료 기준(분). 이 시간을 넘긴 질문은 만료로 취소한다. */
    @Value("${agentdock.delegation.waiting-input-ttl-minutes:720}")
    private long waitingInputTtlMinutes;

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

    /** 위임 실행의 루트 실행을 만든다(스텝은 오케스트레이터가 돌린다). */
    public Started startRoot(Long agentId, Long projectId, Long groupId, String prompt, Long taskId) {
        ExecutionGuard.Target target = guard.prepare(agentId, projectId, groupId);
        Execution root = factory.createRoot(target, prompt, taskId);
        // 루트 실행이 만들어졌다 — 프로젝트를 아는 자리라 projectId 까지 실어 보낸다.
        changeEvents.executionChanged(projectId, root.getId(), taskId);
        return new Started(root, target);
    }

    public record Started(Execution execution, ExecutionGuard.Target target) {
    }

    /** 루트가 만든 git worktree 를 실행에 기록한다(자식은 이 값을 물려받아 같은 디렉터리에서 돈다). */
    public void recordWorktree(Long executionId, String path, String branch) {
        update(executionId, execution -> {
            execution.setWorktreePath(path);
            execution.setWorktreeBranch(branch);
        });
    }

    /**
     * 트리 끝에서 되돌린 결과를 **루트 실행에** 기록한다(트리 결과는 루트의 것이라 자식에는 남기지 않는다).
     * 커밋 sha·병합 상태·사유·변경 파일 목록을 화면(응답)에 그대로 싣는다.
     */
    public void recordTreeResult(Long executionId, MergeStatus mergeStatus, String commit, String detail,
                                 List<ChangedFile> changedFiles) {
        update(executionId, execution -> {
            execution.setMergeStatus(mergeStatus);
            execution.setResultCommit(commit);
            execution.setMergeDetail(detail);
            execution.setChangedFiles(changedFiles == null ? List.of() : changedFiles);
        });
    }

    /**
     * 트리 브랜치를 메인 저장소의 현재 브랜치로 **다시 병합**한다(자동 병합이 MANUAL 로 끝난 뒤, 사람이 변경을
     * 정리하고 화면의 "다시 병합" 버튼으로 부른다). 커밋은 만들지 않고 트리 브랜치를 그대로 병합한다.
     *
     * <p>아직 도는 트리는 409, 워크트리 브랜치가 없으면(격리하지 않았거나 이미 정리됐으면) 404 다.
     * 결과(병합 상태·사유·커밋·변경 파일)는 실행에 다시 기록하고 SYSTEM 로그로도 알린다.
     */
    public ExecutionResponse retryMerge(Long executionId) {
        Execution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new NotFoundException("Execution %d not found".formatted(executionId)));
        if (isRunning(execution.getStatus())) {
            throw new ConflictException("아직 실행 중인 트리는 병합할 수 없습니다");
        }
        String branch = execution.getWorktreeBranch();
        if (branch == null || branch.isBlank()) {
            throw new NotFoundException("Execution %d has no worktree branch".formatted(executionId));
        }
        List<ChangedFile> previous = execution.getChangedFiles() == null ? List.of() : execution.getChangedFiles();
        streamHub.system(executionId, "⎿ 트리 브랜치 %s 를 다시 병합합니다".formatted(branch));
        WorktreeService.TreeResult result = worktreeService.mergeBranch(execution.getWorktreePath(), branch);
        // 이미 병합된 브랜치를 다시 부르면 diff 가 비어 온다 — 그때는 처음 기록한 변경 파일을 그대로 남긴다.
        List<ChangedFile> changedFiles = result.changedFiles().isEmpty() ? previous : result.changedFiles();
        update(executionId, e -> {
            e.setMergeStatus(result.status());
            if (result.commit() != null) {
                e.setResultCommit(result.commit());
            }
            e.setMergeDetail(result.detail());
            e.setChangedFiles(changedFiles);
        });
        logMergeResult(executionId, result, branch);
        return findOne(executionId);
    }

    /**
     * 병합 결과를 SYSTEM 로그로 알린다(자동 병합과 다시 병합이 같은 문구를 쓴다).
     * 커밋 줄은 호출부가 먼저 남긴다 — 여기서는 병합 상태만 말한다.
     */
    public void logMergeResult(Long executionId, WorktreeService.TreeResult result, String treeBranch) {
        switch (result.status()) {
            case MERGED -> streamHub.system(executionId,
                    "⎿ 메인 저장소(%s)로 병합했습니다".formatted(result.repositoryBranch()));
            case MANUAL -> streamHub.system(executionId,
                    "⎿ 자동 병합하지 못했습니다(%s). 브랜치 %s 를 직접 병합하세요".formatted(result.detail(), treeBranch));
            case NONE -> streamHub.system(executionId, "⎿ 커밋·병합을 건너뜁니다: " + result.detail());
        }
    }

    /**
     * 워크트리 정리(사람이 판단해 부른다 — 자동 삭제는 하지 않는다). 아직 도는 트리는 막고(409),
     * 지운 뒤에는 실행에서 경로·브랜치를 비워 화면의 정리 표시도 함께 사라지게 한다.
     */
    public void removeWorktree(Long executionId, boolean deleteBranch) {
        Execution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new NotFoundException("Execution %d not found".formatted(executionId)));
        if (isRunning(execution.getStatus())) {
            throw new ConflictException("아직 실행 중인 트리의 워크트리는 지울 수 없습니다");
        }
        if (execution.getWorktreePath() == null || execution.getWorktreePath().isBlank()) {
            throw new NotFoundException("Execution %d has no worktree".formatted(executionId));
        }
        WorktreeService.Removal removal = worktreeService.remove(
                execution.getWorktreePath(), execution.getWorktreeBranch(), deleteBranch);
        if (!removal.removed()) {
            throw new ConflictException("워크트리를 지우지 못했습니다: " + removal.detail());
        }
        update(executionId, e -> {
            e.setWorktreePath(null);
            e.setWorktreeBranch(null);
        });
    }

    /** 아직 끝나지 않은 상태(PENDING/RUNNING/WAITING_CHILD/WAITING_INPUT)인지. */
    private static boolean isRunning(ExecutionStatus status) {
        return status == ExecutionStatus.PENDING || status == ExecutionStatus.RUNNING
                || status == ExecutionStatus.WAITING_CHILD || status == ExecutionStatus.WAITING_INPUT;
    }

    /**
     * 기동 때 정리할 고아인지. **사람의 답을 기다리는 것(WAITING_INPUT)은 고아가 아니다** —
     * 답을 주면 같은 실행이 이어서 돌기 때문에, 기동했다고 취소하면 사람이 답할 기회를 잃는다.
     */
    private static boolean isOrphan(ExecutionStatus status) {
        return isRunning(status) && status != ExecutionStatus.WAITING_INPUT;
    }

    /**
     * 판단 실행이 사람에게 물어 보고 멈춘다(실행은 끝나지 않는다). 질문(과 보기)을 저장하고 상태를 `WAITING_INPUT` 으로 둔다.
     * 답이 오면(`answer`) 같은 실행의 판단 루프를 이어서 돈다. `options` 는 비어 있어도 된다(보기 없는 질문).
     */
    public void markWaitingInput(Long executionId, String question, List<String> options) {
        update(executionId, execution -> {
            execution.setStatus(ExecutionStatus.WAITING_INPUT);
            execution.setQuestion(question);
            execution.setQuestionOptions(options == null ? List.of() : options);
            // 판단이 끝난 것이 아니므로 완료 시각을 남기지 않는다(화면이 "완료"로 보이면 안 된다).
            execution.setFinishedAt(null);
        });
    }

    /** 사람의 답을 저장한다(다음 판단 스텝의 프롬프트에 질문과 함께 붙는다). */
    public void recordAnswer(Long executionId, String answer) {
        update(executionId, execution -> execution.setAnswer(answer));
    }

    /** 실행 하나를 읽는다(재개에 필요한 값 — 프롬프트·워크트리·질문/답 — 을 꺼내기 위해). */
    public Execution require(Long executionId) {
        return executionRepository.findById(executionId)
                .orElseThrow(() -> new NotFoundException("Execution %d not found".formatted(executionId)));
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
        return applyResult(executionId, result, true);
    }

    /**
     * 한 스텝의 결과(텍스트·계측값)를 저장하고 상태를 돌려준다.
     *
     * <p>`finish=false` 는 **아직 끝난 것이 아닌 스텝**(판단 루프의 중간 스텝)용이다 — 상태·종료시각을 확정하지 않는다.
     * 스텝마다 SUCCEEDED 로 덮으면 화면·알림이 그 순간 "완료"로 보인다(실측: 루트가
     * `RUNNING→SUCCEEDED→RUNNING→WAITING_CHILD` 로 찍히고, 그 사이 총괄이 끝난 것처럼 보였다).
     * 실패는 중간 스텝이라도 그대로 확정한다 — 사람이 알아야 하기 때문이다.
     */
    public ExecutionStatus applyResult(Long executionId, AgentExecutionResult result, boolean finish) {
        ExecutionStatus status = result.succeeded() ? ExecutionStatus.SUCCEEDED : ExecutionStatus.FAILED;
        boolean persist = finish || !result.succeeded();
        update(executionId, execution -> {
            ExecutionMetrics metrics = result.metrics();
            execution.setResultText(result.resultText());
            execution.setExitCode(result.exitCode());
            if (persist) {
                execution.setStatus(status);
                execution.setFinishedAt(Instant.now());
            }
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

    /** 서버 기동 시 PENDING/RUNNING/WAITING_CHILD 로 남은 고아 실행을 일괄 CANCELLED 로 전이하고,
     * 루트 실행이면 Task 도 publishFinished 경로로 종료한다. 이미 끝난 실행은 건드리지 않는다.
     * 사람의 답을 기다리는 WAITING_INPUT 은 남긴다 — 답을 주면 같은 실행이 이어서 돌기 때문이다.
     * 기동 트리거가 부를 수 있도록 public 이다(기동 1회만, 상시 감시 없음).
     *
     * @return 정리한 실행 수
     */
    public int cleanupOrphanExecutions() {
        List<Execution> orphans = executionRepository.findAll().stream()
                .filter(execution -> isOrphan(execution.getStatus()))
                .toList();
        for (Execution orphan : orphans) {
            cancelExecution(orphan, STALE_EXECUTION_REASON);
        }
        return orphans.size();
    }

    /**
     * 서버 기동 시 한 번 고아 실행을 정리한다(상시 감시는 하지 않는다).
     * WorkspaceService 의 기동 워밍업과 같은 방식으로 ApplicationReadyEvent 에 붙인다.
     */
    @EventListener(ApplicationReadyEvent.class)
    void cleanupOrphansOnStartup() {
        int cleaned = cleanupOrphanExecutions();
        if (cleaned > 0) {
            log.info("cleaned up {} orphan execution(s) from a previous run", cleaned);
        }
    }

    /**
     * 실행 취소. 그 실행과 **그 아래 위임 자식들**을 모두 멈춘다 —
     * 자식들은 각자 CLI 프로세스를 갖고 있어 부모만 멈추면 계속 돌고, 사용자 눈에는 취소가 안 된 것처럼 보인다.
     * 이미 끝난 실행은 상태를 건드리지 않지만, 그 아래에서 아직 도는 것은 멈춘다.
     */
    public Map<String, Boolean> cancel(Long id) {
        Execution execution = executionRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Execution %d not found".formatted(id)));
        return Map.of("cancelled", cancelSubtree(execution, MANUAL_CANCEL_REASON));
    }

    /** 이 실행과 자손을 모두 취소한다(하나라도 실제로 전이했으면 true). */
    private boolean cancelSubtree(Execution execution, String reason) {
        boolean cancelled = cancelOne(execution, reason);
        for (Execution descendant : descendantsOf(execution)) {
            // 자손은 하나가 이미 끝나 있어도 나머지를 계속 멈춰야 하므로 숏컷하지 않는다.
            cancelled = cancelOne(descendant, reason) || cancelled;
        }
        return cancelled;
    }

    /** 이 실행 아래의 자손 전부(자식 → 손자 순). 위임 트리는 부모 링크로만 이어진다. */
    private List<Execution> descendantsOf(Execution execution) {
        Long rootId = execution.getRootExecutionId() == null ? execution.getId() : execution.getRootExecutionId();
        List<Execution> tree = executionRepository.findByRootExecutionIdOrderByIdAsc(rootId);
        List<Execution> descendants = new ArrayList<>();
        List<Long> pending = new ArrayList<>();
        pending.add(execution.getId());
        for (int i = 0; i < pending.size(); i++) {
            Long parentId = pending.get(i);
            for (Execution candidate : tree) {
                if (parentId.equals(candidate.getParentExecutionId())) {
                    descendants.add(candidate);
                    pending.add(candidate.getId());
                }
            }
        }
        return descendants;
    }

    /** 실행 하나를 실제로 멈춘다: CLI 프로세스 종료 + 상태 전이. */
    private boolean cancelOne(Execution execution, String reason) {
        killProcess(execution);
        return cancelExecution(execution, reason);
    }

    /**
     * 답이 오지 않은 채 오래된 입력 대기 질문을 만료 처리한다(취소).
     * 답이 오면 이어서 도는 실행이라 방치하면 그 트리는 영원히 멈춰 있다 — 만료로 끝내 화면이 정리되게 한다.
     * 주기 실행은 앱의 {@code @EnableScheduling} 이 켠다. 기준은 설정(기본 12시간)으로 바꾼다.
     *
     * @return 만료 처리한 실행 수
     */
    @Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT1M")
    public int expireStaleWaitingInputs() {
        Instant deadline = Instant.now().minus(Duration.ofMinutes(waitingInputTtlMinutes));
        List<Execution> stale = executionRepository.findAll().stream()
                .filter(execution -> execution.getStatus() == ExecutionStatus.WAITING_INPUT)
                .filter(execution -> execution.getUpdatedAt() == null || execution.getUpdatedAt().isBefore(deadline))
                .toList();
        for (Execution execution : stale) {
            cancelSubtree(execution, WAITING_INPUT_EXPIRED_REASON);
        }
        if (!stale.isEmpty()) {
            log.info("[execution] 입력 대기 만료로 {}건을 취소했습니다 (기준 {}분)", stale.size(), waitingInputTtlMinutes);
        }
        return stale.size();
    }

    /** 그 실행의 CLI 프로세스를 종료한다(에이전트를 못 찾아도 상태 전이는 계속한다). */
    private void killProcess(Execution execution) {
        if (execution.getAgentId() == null) {
            return;
        }
        agentRepository.findByIdWithRelations(execution.getAgentId()).ifPresent(agent ->
                runtimeRegistry.resolve(agent.getProvider().getKey().name())
                        .cancel(String.valueOf(execution.getId())));
    }

    /** 실행을 CANCELLED 로 전이하고 사유를 남긴다(이미 끝난 실행은 건드리지 않는다).
     * 루트 실행(root_execution_id 가 비어 있음)이면 Task 상태도 publishFinished 경로로 동기화한다.
     *
     * @return 실제로 전이했으면 true
     */
    private boolean cancelExecution(Execution execution, String reason) {
        if (!isRunning(execution.getStatus())) {
            return false;
        }
        execution.setStatus(ExecutionStatus.CANCELLED);
        execution.setFinishedAt(Instant.now());
        execution.setErrorMessage(reason);
        executionRepository.save(execution);
        changeEvents.executionChanged(projectIdOf(execution), execution.getId(), execution.getTaskId());
        if (execution.getRootExecutionId() == null) {
            publishFinished(execution.getId(), execution.getTaskId(), ExecutionStatus.CANCELLED);
        }
        return true;
    }

    /** 실행 하나를 바꿔 저장하고, 바뀌었다고 전역 스트림에 알린다(상태·계약·결과·워크트리 모두 이 길로 지난다). */
    private void update(Long executionId, Consumer<Execution> change) {
        Execution execution = executionRepository.findById(executionId)
                .orElseThrow(() -> new NotFoundException("Execution %d not found".formatted(executionId)));
        ExecutionStatus before = execution.getStatus();
        String beforeSignature = signatureOf(execution);
        change.accept(execution);
        executionRepository.save(execution);
        // 상태가 그대로여도 병합·커밋·질문처럼 사람이 궁금해할 변화가 있으면 남긴다(그 밖의 잔변경은 DEBUG).
        if (!beforeSignature.equals(signatureOf(execution))) {
            logTransition(execution, before);
        } else {
            log.debug("[execution] {} 갱신 (상태 {} 그대로)", execution.getId(), execution.getStatus());
        }
        changeEvents.executionChanged(projectIdOf(execution), executionId, execution.getTaskId());
    }

    /**
     * 한 줄 로그를 남길 만한 변화가 있었는지 판단하는 서명(상태·병합·커밋·워크트리·질문).
     * 지표(토큰·비용)와 결과 본문은 상태 변화에 함께 실리므로 여기 넣지 않는다.
     */
    private static String signatureOf(Execution execution) {
        return "%s|%s|%s|%s|%s".formatted(
                execution.getStatus(),
                execution.getMergeStatus(),
                execution.getResultCommit(),
                execution.getWorktreeBranch(),
                execution.getQuestion() == null ? "" : execution.getQuestion());
    }

    /**
     * 실행 하나의 상태 전이를 한 줄로 남긴다(콘솔과 파일에 함께). 지표는 **있는 것만** 붙인다 —
     * 없는 값을 0 이나 `-` 로 지어내지 않는다.
     */
    private void logTransition(Execution execution, ExecutionStatus before) {
        StringBuilder line = new StringBuilder("execution=%d agent=%s task=%s %s->%s".formatted(
                execution.getId(),
                execution.getAgentId() == null ? "-" : execution.getAgentId(),
                execution.getTaskId() == null ? "-" : execution.getTaskId(),
                before == null ? "-" : before,
                execution.getStatus()));
        if (execution.getExitCode() != null) {
            line.append(" exit=").append(execution.getExitCode());
        }
        if (execution.getDurationMs() != null) {
            line.append(" ms=").append(execution.getDurationMs());
        }
        if (execution.getInputTokens() != null) {
            line.append(" in=").append(execution.getInputTokens());
        }
        if (execution.getOutputTokens() != null) {
            line.append(" out=").append(execution.getOutputTokens());
        }
        if (execution.getCostUsd() != null) {
            line.append(" cost=").append(execution.getCostUsd());
        }
        if (execution.getErrorMessage() != null) {
            line.append(" error=").append(execution.getErrorMessage());
        }
        if (execution.getMergeStatus() != null) {
            line.append(" merge=").append(execution.getMergeStatus());
        }
        if (execution.getResultCommit() != null) {
            line.append(" commit=").append(execution.getResultCommit());
        }
        if (execution.getWorktreeBranch() != null) {
            line.append(" wt=").append(execution.getWorktreeBranch());
        }
        log.info("[execution] {}", line);
    }

    /** 실행 에이전트의 프로젝트 id(화면이 어느 프로젝트를 다시 읽을지 정하는 데 쓴다). 모르면 null. */
    private Long projectIdOf(Execution execution) {
        if (execution.getAgentId() == null) {
            return null;
        }
        return agentRepository.findById(execution.getAgentId()).map(Agent::getProjectId).orElse(null);
    }
}
