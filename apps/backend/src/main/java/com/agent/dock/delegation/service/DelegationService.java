package com.agent.dock.delegation.service;

import com.agent.dock.agent.domain.Agent;
import com.agent.dock.agent.repository.AgentRepository;
import com.agent.dock.common.exception.BadRequestException;
import com.agent.dock.common.exception.ConflictException;
import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.delegation.dto.DelegationContract;
import com.agent.dock.delegation.util.DelegationPrompts;
import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.domain.ExecutionDecision;
import com.agent.dock.execution.domain.ExecutionStatus;
import com.agent.dock.execution.dto.ExecutionResponse;
import com.agent.dock.execution.repository.ExecutionRepository;
import com.agent.dock.execution.service.ExecutionFactory;
import com.agent.dock.execution.service.ExecutionGuard;
import com.agent.dock.execution.service.ExecutionRunner;
import com.agent.dock.execution.service.ExecutionService;
import com.agent.dock.execution.service.ExecutionStreamHub;
import com.agent.dock.execution.service.WorktreeService;
import com.agent.dock.group.dto.GroupResponse;
import com.agent.dock.group.service.GroupService;
import com.agent.dock.project.domain.Project;
import com.agent.dock.project.repository.ProjectRepository;
import com.agent.dock.runtime.dto.AgentExecutionResult;
import com.agent.dock.runtime.util.JsonObjects;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 위임 실행(실행 트리)의 오케스트레이터.
 *
 * <p>명령 하나가 실행 트리 하나다. 판단이 필요한 실행(마스터·그룹 리더)은 계약(JSON)을 남기고,
 * 계약이 delegate 면 자식 실행을 만들어 결과를 기다린 뒤(그동안 부모는 WAITING_CHILD) 결과를 붙여 다시 판단한다.
 * done 이 나오면 끝난다. 상한(깊이·실행 수·예산)과 순환 위임은 백엔드가 막는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DelegationService {

    private final ExecutionService executionService;
    private final ExecutionRunner runner;
    private final ExecutionStreamHub streamHub;
    private final ExecutionFactory executionFactory;
    private final ExecutionRepository executionRepository;
    private final ExecutionGuard guard;
    private final AgentRepository agentRepository;
    private final ProjectRepository projectRepository;
    private final GroupService groupService;
    private final RuleRouter ruleRouter;
    private final WorktreeService worktreeService;

    @Value("${agentdock.delegation.max-depth:3}")
    private int maxDepth;
    @Value("${agentdock.delegation.max-executions:20}")
    private int maxExecutions;
    @Value("${agentdock.delegation.max-steps:4}")
    private int maxSteps;
    @Value("${agentdock.delegation.max-targets:4}")
    private int maxTargets;
    @Value("${agentdock.delegation.max-concurrent:4}")
    private int maxConcurrent;
    @Value("${agentdock.delegation.max-cost-usd:5}")
    private BigDecimal maxCostUsd;

    /** CLI 를 동시에 돌리는 수. 부모가 자식을 기다리는 동안에도 잡지 않으므로 교착이 없다. */
    private Semaphore cliSlots;
    private final ExecutorService workers = Executors.newCachedThreadPool();

    @PostConstruct
    void initSlots() {
        this.cliSlots = new Semaphore(Math.max(1, maxConcurrent), true);
    }

    private record TreeState(Long projectId, Long taskId, Long rootExecutionId, String worktreePath,
                             String worktreeBranch) {
    }

    /**
     * 프로젝트 명령을 받아 실행 트리를 시작한다. 루트 실행을 즉시 돌려주고 나머지는 백그라운드에서 돈다.
     * 대상(targetAgentId)을 주지 않으면 프로젝트 마스터가 받는다.
     */
    public Execution start(Long projectId, Long targetAgentId, Long groupId, String text, Long taskId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project %d not found".formatted(projectId)));
        Long rootAgentId = resolveRootAgent(project, targetAgentId, groupId);

        ExecutionService.Started started = executionService.startRoot(rootAgentId, projectId, groupId, text, taskId);
        Execution root = started.execution();
        streamHub.open(root.getId());

        // 이 명령(트리) 전체를 워크스페이스 저장소의 git worktree 로 격리한다. 못 만들면 워크스페이스에서 계속 돈다.
        ExecutionGuard.Target target = isolated(root, started.target());

        boolean judgement = isJudgementAgent(rootAgentId, projectId);
        TreeState state = new TreeState(projectId, taskId, root.getId(), target.worktreePath(), target.worktreeBranch());
        workers.submit(() -> {
            ExecutionStatus status = runExecution(root, target, text, judgement, 0, List.of(rootAgentId), state);
            // 사람의 답을 기다리는 중이면 트리는 끝난 것이 아니다 — 결과를 되돌리지도, 스트림을 닫지도 않는다.
            if (status == ExecutionStatus.WAITING_INPUT) {
                return;
            }
            // 트리가 끝나면 그 워크트리의 결과를 커밋해 메인 저장소로 되돌린다(되돌리지 못하면 사유와 브랜치를 알린다).
            // 스트림을 닫기 전에 하여 로그가 라이브로도 보이게 한다.
            collectTreeResult(root, target);
            closeStream(root.getId(), status);
            executionService.publishFinished(root.getId(), taskId, status);
        });
        return root;
    }

    // ── 사람에게 묻고 답을 받아 이어서 돈다 (`ask` 계약) ──────────────────────────────

    /**
     * 사람의 답을 저장하고 **같은 실행의 판단 루프를 이어서** 돌린다(`WAITING_INPUT` → 다시 판단).
     * 질문과 답은 다음 스텝 프롬프트의 "지난 단계 결과"에 그대로 들어가고, 스텝 상한은 그대로 적용된다.
     *
     * <p>재개는 실행 행에 남아 있는 값(지시·워크트리·트리)과 조상 사슬(깊이·경로)로 문맥을 다시 만든다 —
     * 스텝 루프가 스레드를 넘어 이어지기 때문이다.
     */
    public ExecutionResponse answer(Long executionId, String text) {
        Execution execution = executionService.require(executionId);
        if (execution.getStatus() != ExecutionStatus.WAITING_INPUT) {
            throw new ConflictException("입력 대기 상태의 실행이 아닙니다");
        }
        String answer = text == null ? "" : text.strip();
        executionService.recordAnswer(executionId, answer);
        streamHub.system(executionId, "⎿ 답변: " + answer);
        resume(execution, answer);
        return executionService.findOne(executionId);
    }

    /** 멈춰 있던 판단 실행을 이어서 돈다. 끝나면 루트의 트리 결과를 되돌리고 스트림을 닫는다. */
    private void resume(Execution asked, String answer) {
        Long agentId = asked.getAgentId();
        Long projectId = requireAgent(agentId).getProjectId();
        Long groupId = groupIdOf(agentId, projectId);
        ExecutionGuard.Target prepared = guard.prepare(agentId, projectId, groupId);
        ExecutionGuard.Target target = asked.getWorktreePath() == null || asked.getWorktreePath().isBlank()
                ? prepared
                : prepared.withWorktree(asked.getWorktreePath(), asked.getWorktreeBranch());
        Long rootId = asked.getRootExecutionId() == null ? asked.getId() : asked.getRootExecutionId();
        TreeState state = new TreeState(projectId, asked.getTaskId(), rootId,
                target.worktreePath(), target.worktreeBranch());
        int depth = depthOf(asked);
        List<Long> path = pathOf(asked);
        boolean judgement = isJudgementAgent(agentId, projectId);
        boolean root = asked.getParentExecutionId() == null;
        String progress = DelegationPrompts.answerContext(asked.getQuestion(), answer);

        workers.submit(() -> {
            ExecutionStatus status = judgement
                    ? runJudgement(asked, target, asked.getPrompt(), depth, path, state, progress)
                    : runWork(asked, target, asked.getPrompt());
            if (status == ExecutionStatus.WAITING_INPUT) {
                return; // 또 물었으면 다시 기다린다.
            }
            if (root) {
                collectTreeResult(asked, target);
            }
            closeStream(asked.getId(), status);
            executionService.publishFinished(asked.getId(), asked.getTaskId(), status);
        });
    }

    /** 실행이 트리에서 몇 단계 아래인지(루트 = 0). 조상 사슬을 타고 올라가며 센다. */
    private int depthOf(Execution execution) {
        int depth = 0;
        Long parentId = execution.getParentExecutionId();
        while (parentId != null) {
            depth++;
            parentId = executionRepository.findById(parentId).map(Execution::getParentExecutionId).orElse(null);
        }
        return depth;
    }

    /** 루트에서 이 실행까지의 에이전트 경로(순환 위임 판정에 쓴다). */
    private List<Long> pathOf(Execution execution) {
        List<Long> agents = new ArrayList<>();
        Execution current = execution;
        while (current != null) {
            agents.add(current.getAgentId());
            Long parentId = current.getParentExecutionId();
            current = parentId == null ? null : executionRepository.findById(parentId).orElse(null);
        }
        return agents.reversed();
    }

    /**
     * 루트 실행의 워크스페이스가 git 저장소면 worktree 를 만들어 그 경로를 실행에 기록하고 로그로 알린다.
     * 만들지 못하면(저장소가 아니거나 git 실패) 워크스페이스 그대로 돌린다 — 격리 실패가 실행을 막지 않는다.
     */
    private ExecutionGuard.Target isolated(Execution root, ExecutionGuard.Target target) {
        WorktreeService.Worktree worktree = worktreeService.create(target.workspacePath(), root.getId());
        if (worktree.created()) {
            executionService.recordWorktree(root.getId(), worktree.path(), worktree.branch());
            streamHub.system(root.getId(),
                    "⎿ 워크트리: %s (브랜치 %s)".formatted(worktree.path(), worktree.branch()));
            return target.withWorktree(worktree.path(), worktree.branch());
        }
        streamHub.system(root.getId(),
                "⎿ 워크트리를 만들지 못해 워크스페이스에서 실행합니다: " + worktree.reason());
        return target;
    }

    // ── 트리 결과 되돌리기 (트리 브랜치에 커밋 + 메인 저장소로 병합) ──────────────────

    /**
     * 트리(루트 실행)가 끝났으니 그 워크트리의 변경을 트리 브랜치에 **커밋 하나**로 남기고 메인 저장소로 되돌린다.
     * 격리하지 못한 트리(워크트리 없음)는 되돌릴 것이 없다. 결과(커밋 sha·병합 상태·사유·변경 파일)는 루트 실행에
     * 기록하고 SYSTEM 로그로 알린다 — 병합하지 못하면 사용자가 직접 병합하도록 브랜치를 알려 준다.
     */
    private void collectTreeResult(Execution root, ExecutionGuard.Target target) {
        String worktreePath = target.worktreePath();
        if (worktreePath == null || worktreePath.isBlank()) {
            return;
        }
        Long rootId = root.getId();
        Execution fresh = executionRepository.findById(rootId).orElse(root);
        String message = commitMessage(fresh);
        String body = "실행 #%d\n참여: %s".formatted(rootId, String.join(", ", participants(rootId)));
        WorktreeService.TreeResult result = worktreeService.collect(
                target.workspacePath(), worktreePath, target.worktreeBranch(), message, body);
        executionService.recordTreeResult(rootId, result.status(), result.commit(), result.detail(),
                result.changedFiles());
        logTreeResult(rootId, result, target.worktreeBranch());
    }

    /** 커밋 제목: 루트 실행의 최종 요약(계약 done.summary / result_text)을 다듬어 쓴다. 없으면 `실행 #<id> 작업 결과`. */
    private String commitMessage(Execution root) {
        Map<String, Object> handoff = root.getHandoff();
        if (handoff != null && handoff.get("summary") instanceof String summary && !summary.isBlank()) {
            return summary.strip();
        }
        String resultText = root.getResultText();
        if (resultText != null && !resultText.isBlank()) {
            // 결과 텍스트가 계약 JSON(코드블록·앞뒤 설명 포함)이면 그 summary 만 꺼내 쓴다.
            Optional<DelegationContract> contract = DelegationContract.parse(JsonObjects.objectIn(resultText));
            if (contract.isPresent() && !contract.get().summary().isBlank()) {
                return contract.get().summary().strip();
            }
            return resultText.strip();
        }
        return "실행 #%d 작업 결과".formatted(root.getId());
    }

    /** 트리에 참여한 에이전트 이름(중복 없이, 루트 → 자식 순서). 커밋 본문에 넣는다. */
    private List<String> participants(Long rootExecutionId) {
        List<Long> agentIds = new ArrayList<>();
        executionRepository.findById(rootExecutionId).ifPresent(root -> agentIds.add(root.getAgentId()));
        executionRepository.findByRootExecutionIdOrderByIdAsc(rootExecutionId)
                .forEach(child -> agentIds.add(child.getAgentId()));
        List<String> names = new ArrayList<>();
        for (Long agentId : agentIds) {
            if (agentId == null) {
                continue;
            }
            String name = agentName(agentId);
            if (!names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    /** 되돌린 결과를 SYSTEM 로그로 알린다(터미널 창에서 그대로 보인다). */
    private void logTreeResult(Long rootId, WorktreeService.TreeResult result, String treeBranch) {
        if (result.commit() != null) {
            streamHub.system(rootId, "⎿ 변경 %d개를 커밋했습니다 (%s)"
                    .formatted(result.changedFiles().size(), result.commit()));
        }
        executionService.logMergeResult(rootId, result, treeBranch);
    }

    // ── 실행 하나를 돌린다(자식은 재귀로 각자 돈다) ────────────────────────────────

    private ExecutionStatus runExecution(Execution execution, ExecutionGuard.Target target, String request,
                                         boolean judgement, int depth, List<Long> path, TreeState state) {
        Long id = execution.getId();
        try {
            return judgement
                    ? runJudgement(execution, target, request, depth, path, state)
                    : runWork(execution, target, request);
        } catch (Exception ex) {
            log.error("execution {} failed", id, ex);
            executionService.markFailed(id, ex);
            return ExecutionStatus.FAILED;
        }
    }

    /** 끝난 실행의 스트림을 닫는다(끝난 상태와 exit code 를 알린다). 루트는 트리 결과를 되돌린 뒤에 닫는다. */
    private void closeStream(Long id, ExecutionStatus status) {
        Integer exitCode = executionRepository.findById(id).map(Execution::getExitCode).orElse(null);
        streamHub.close(id, status, exitCode);
    }

    /** 일하는 실행: 계약 없이 한 번 돌리고 Handoff 를 남긴다. */
    private ExecutionStatus runWork(Execution execution, ExecutionGuard.Target target, String request) {
        executionService.markRunning(execution.getId());
        Agent agent = requireAgent(target.agentId());
        AgentExecutionResult result = step(execution.getId(), agent, target,
                DelegationPrompts.work(request, execution.getPrompt()));
        return applyStep(execution.getId(), result);
    }

    /** 판단하는 실행: 계약을 남기고, 위임하면 자식을 기다렸다가 결과를 붙여 다시 판단한다. */
    private ExecutionStatus runJudgement(Execution execution, ExecutionGuard.Target target, String request,
                                         int depth, List<Long> path, TreeState state) {
        return runJudgement(execution, target, request, depth, path, state, "");
    }

    /**
     * 판단 루프. `initialProgress` 는 "지난 단계 결과"의 첫 내용이다 — 자식 결과를 기다린 재판단뿐 아니라
     * 사람의 답을 받아 이어서 도는 재개(`ask`)도 같은 자리에 질문·답을 넣는다.
     */
    private ExecutionStatus runJudgement(Execution execution, ExecutionGuard.Target target, String request,
                                         int depth, List<Long> path, TreeState state, String initialProgress) {
        Long id = execution.getId();
        Project project = projectRepository.findById(state.projectId()).orElseThrow();
        List<GroupResponse> groups = groupService.findAll(state.projectId());
        String roster = rosterOf(groups);
        String progress = initialProgress == null ? "" : initialProgress;
        boolean retryUsed = false;

        for (int step = 0; step < maxSteps; step++) {
            executionService.markRunning(id);
            Agent agent = requireAgent(target.agentId());
            String prompt = DelegationPrompts.judgement(project.getName(), roster, request, progress, maxTargets);
            AgentExecutionResult result = step(id, agent, target, prompt);
            ExecutionStatus status = applyStep(id, result);
            if (!result.succeeded()) {
                streamHub.system(id, "⎿ 실행이 실패해 사람에게 넘깁니다");
                return status;
            }
            // 판단은 계속된다. 스텝 종료 상태가 화면에 '완료'로 보이지 않게 다시 실행 중으로 돌린다.
            executionService.markRunning(id);

            Optional<DelegationContract> contract = DelegationContract.parse(result.structured());
            if (contract.isEmpty() && !retryUsed) {
                retryUsed = true;
                streamHub.system(id, "⎿ 계약을 읽지 못해 한 번 더 시도합니다");
                result = step(id, agent, target, DelegationPrompts.repair(result.resultText()));
                status = applyStep(id, result);
                if (!result.succeeded()) {
                    return status;
                }
                executionService.markRunning(id);
                contract = DelegationContract.parse(result.structured());
            }
            if (contract.isEmpty()) {
                contract = ruleFallback(id, request, groups);
            }
            if (contract.isEmpty()) {
                executionService.markEscalated(id, "계약을 읽지 못해 사람에게 넘깁니다");
                return ExecutionStatus.FAILED;
            }

            DelegationContract decided = contract.get();
            if (decided.action() == ExecutionDecision.DONE) {
                executionService.recordDecision(id, ExecutionDecision.DONE, null);
                executionService.recordHandoff(id, Map.of("summary", decided.summary()));
                executionService.markSucceeded(id);
                streamHub.system(id, "⎿ 마무리합니다");
                return ExecutionStatus.SUCCEEDED;
            }

            // 혼자 정할 수 없는 갈림길: 사람에게 묻고 멈춘다(실행은 끝나지 않는다 — 답이 오면 이어서 돈다).
            if (decided.action() == ExecutionDecision.ASK) {
                String question = decided.question().isBlank() ? request.strip() : decided.question().strip();
                executionService.recordDecision(id, ExecutionDecision.ASK, null);
                executionService.markWaitingInput(id, question);
                streamHub.system(id, "⎿ 질문: " + question);
                if (!decided.options().isEmpty()) {
                    streamHub.system(id, "⎿ 보기: " + String.join(" / ", decided.options()));
                }
                return ExecutionStatus.WAITING_INPUT;
            }

            List<DelegationContract.Order> orders = acceptableOrders(id, decided, groups, depth, path, state);
            if (orders.isEmpty()) {
                executionService.markEscalated(id, "맡길 수 있는 대상을 찾지 못해 사람에게 넘깁니다");
                return ExecutionStatus.FAILED;
            }
            executionService.recordDecision(id, ExecutionDecision.DELEGATE, orders.get(0).agentId());
            executionService.markWaitingChild(id);
            streamHub.system(id, "⎿ %d건을 맡기고 결과를 기다립니다".formatted(orders.size()));

            List<DelegationPrompts.ChildOutcome> outcomes = runChildren(execution, orders, depth + 1, path, state, request);
            // 하위 실행이 사람의 입력을 기다리면 이 실행도 멈춘다(트리는 끝나지 않는다). 질문은 위로 올려
            // 화면(채팅)에서 한 번만 보이게 한다.
            Optional<DelegationPrompts.ChildOutcome> waiting = outcomes.stream()
                    .filter(DelegationPrompts.ChildOutcome::waiting)
                    .findFirst();
            if (waiting.isPresent()) {
                String question = waiting.get().note().isBlank() ? request.strip() : waiting.get().note();
                executionService.markWaitingInput(id, question);
                streamHub.system(id, "⎿ 하위 실행이 사람의 입력을 기다립니다");
                return ExecutionStatus.WAITING_INPUT;
            }
            progress = DelegationPrompts.childResults(outcomes);
        }

        executionService.markEscalated(id, "판단을 %d번 반복해 중단합니다".formatted(maxSteps));
        return ExecutionStatus.FAILED;
    }

    // ── 자식 실행 ────────────────────────────────────────────────────────────────

    private List<DelegationPrompts.ChildOutcome> runChildren(Execution parent, List<DelegationContract.Order> orders,
                                                             int depth, List<Long> path, TreeState state, String request) {
        List<Future<DelegationPrompts.ChildOutcome>> futures = new ArrayList<>();
        for (DelegationContract.Order order : orders) {
            futures.add(workers.submit(() -> runChild(parent, order, depth, path, state, request)));
        }
        List<DelegationPrompts.ChildOutcome> outcomes = new ArrayList<>();
        for (Future<DelegationPrompts.ChildOutcome> future : futures) {
            try {
                outcomes.add(future.get());
            } catch (Exception ex) {
                log.warn("child execution failed: {}", ex.getMessage());
                outcomes.add(new DelegationPrompts.ChildOutcome("알 수 없음", null, "실패", String.valueOf(ex), List.of(), ""));
            }
        }
        return outcomes;
    }

    private DelegationPrompts.ChildOutcome runChild(Execution parent, DelegationContract.Order order, int depth,
                                                    List<Long> path, TreeState state, String request) {
        Long childAgentId = order.agentId();
        ExecutionGuard.Target prepared = guard.prepare(childAgentId, state.projectId(),
                groupIdOf(childAgentId, state.projectId()));
        // 루트가 만든 worktree 가 있으면 자식도 그 안에서 돈다(부모의 편집을 봐야 하므로 같은 디렉터리).
        ExecutionGuard.Target target = state.worktreePath() == null
                ? prepared
                : prepared.withWorktree(state.worktreePath(), state.worktreeBranch());
        String childPrompt = order.expects().isBlank()
                ? order.prompt()
                : order.prompt() + "\n\n[기대 결과]\n" + order.expects();
        Execution child = executionFactory.createChild(target, childPrompt, state.taskId(), parent, parent);
        streamHub.open(child.getId());

        boolean judgement = isJudgementAgent(childAgentId, state.projectId());
        ExecutionStatus status = runExecution(child, target, request, judgement, depth, append(path, childAgentId), state);
        // 사람의 답을 기다리는 자식은 끝난 것이 아니다 — 스트림을 닫지 않는다(답이 오면 이어서 돈다).
        if (status != ExecutionStatus.WAITING_INPUT) {
            closeStream(child.getId(), status);
        }
        return outcomeOf(childAgentId, status, child.getId());
    }

    private DelegationPrompts.ChildOutcome outcomeOf(Long agentId, ExecutionStatus status, Long executionId) {
        Execution execution = executionRepository.findById(executionId).orElseThrow();
        Map<String, Object> handoff = execution.getHandoff();
        String summary = handoff == null ? "" : String.valueOf(handoff.getOrDefault("summary", ""));
        List<String> changedFiles = handoff != null && handoff.get("changedFiles") instanceof List<?> files
                ? files.stream().map(String::valueOf).toList()
                : List.of();
        boolean waiting = status == ExecutionStatus.WAITING_INPUT;
        String note = waiting && execution.getQuestion() != null ? execution.getQuestion() : "";
        return new DelegationPrompts.ChildOutcome(agentName(agentId), agentId, statusLabel(status), summary,
                changedFiles, note, waiting);
    }

    // ── 가드 ─────────────────────────────────────────────────────────────────────

    /** 맡길 수 있는 주문만 남긴다. 거부 사유는 실행 로그에 남겨 화면에서 보이게 한다. */
    private List<DelegationContract.Order> acceptableOrders(Long executionId, DelegationContract contract,
                                                           List<GroupResponse> groups, int depth, List<Long> path, TreeState state) {
        Set<Long> allowed = agentsIn(groups);
        List<DelegationContract.Order> accepted = new ArrayList<>();
        if (contract.orders().size() > maxTargets) {
            streamHub.system(executionId, "⎿ 한 번에 맡길 수 있는 수(%d)를 넘어 앞의 %d건만 받습니다"
                    .formatted(maxTargets, maxTargets));
        }
        for (DelegationContract.Order order : contract.orders()) {
            if (accepted.size() >= maxTargets) {
                break;
            }
            String rejection = rejectionReason(order, allowed, depth, path, state);
            if (rejection != null) {
                streamHub.system(executionId, "⎿ 위임 거부: " + rejection);
                continue;
            }
            accepted.add(order);
        }
        return accepted;
    }

    private String rejectionReason(DelegationContract.Order order, Set<Long> allowed, int depth, List<Long> path, TreeState state) {
        if (!allowed.contains(order.agentId())) {
            return "이 프로젝트의 팀에 없는 에이전트입니다(id=%d)".formatted(order.agentId());
        }
        if (path.contains(order.agentId())) {
            return "순환 위임입니다(id=%d 는 이 경로에 이미 있습니다)".formatted(order.agentId());
        }
        if (depth + 1 > maxDepth) {
            return "깊이 상한(%d)을 넘습니다".formatted(maxDepth);
        }
        if (treeSize(state.rootExecutionId()) + 1 > maxExecutions) {
            return "트리 실행 수 상한(%d)을 넘습니다".formatted(maxExecutions);
        }
        BigDecimal cost = treeCost(state.rootExecutionId());
        if (cost.compareTo(maxCostUsd) >= 0) {
            return "예산 상한($%s)을 넘었습니다(현재 $%s)".formatted(maxCostUsd.toPlainString(), cost.toPlainString());
        }
        return null;
    }

    private long treeSize(Long rootExecutionId) {
        return 1 + executionRepository.countByRootExecutionId(rootExecutionId);
    }

    private BigDecimal treeCost(Long rootExecutionId) {
        BigDecimal root = executionRepository.findById(rootExecutionId).map(Execution::getCostUsd).orElse(null);
        BigDecimal children = executionRepository.sumCostByRootExecutionId(rootExecutionId);
        return (root == null ? BigDecimal.ZERO : root).add(children == null ? BigDecimal.ZERO : children);
    }

    // ── 규칙·조회 도우미 ─────────────────────────────────────────────────────────

    /** 계약을 못 읽었을 때의 폴백: 요청 문장으로 팀을 고른다(규칙 먼저). */
    private Optional<DelegationContract> ruleFallback(Long executionId, String request, List<GroupResponse> groups) {
        List<RuleRouter.Team> teams = groups.stream()
                .map(group -> new RuleRouter.Team(group.id(), group.name(),
                        group.leader() == null ? null : group.leader().id()))
                .toList();
        Optional<Long> leaderId = ruleRouter.route(request, teams);
        leaderId.ifPresent(id -> streamHub.system(executionId, "⎿ 규칙으로 대상을 정했습니다(에이전트 id=%d)".formatted(id)));
        return leaderId.map(id -> DelegationContract.delegate(
                List.of(new DelegationContract.Order(id, request, "")), ""));
    }

    private boolean isJudgementAgent(Long agentId, Long projectId) {
        Project project = projectRepository.findById(projectId).orElseThrow();
        if (agentId.equals(project.getMasterAgentId())) {
            return true;
        }
        return groupService.findAll(projectId).stream()
                .anyMatch(group -> group.leader() != null && agentId.equals(group.leader().id()));
    }

    /** 에이전트가 실행될 때 끼는 그룹 프롬프트: 리더인 그룹을 먼저, 없으면 속한 첫 그룹. */
    private Long groupIdOf(Long agentId, Long projectId) {
        List<GroupResponse> groups = groupService.findAll(projectId);
        return groups.stream()
                .filter(group -> group.leader() != null && agentId.equals(group.leader().id()))
                .findFirst()
                .map(GroupResponse::id)
                .orElseGet(() -> groups.stream()
                        .filter(group -> group.members().stream().anyMatch(member -> agentId.equals(member.id())))
                        .findFirst()
                        .map(GroupResponse::id)
                        .orElse(null));
    }

    private Set<Long> agentsIn(List<GroupResponse> groups) {
        Set<Long> ids = new HashSet<>();
        for (GroupResponse group : groups) {
            if (group.leader() != null) {
                ids.add(group.leader().id());
            }
            group.members().forEach(member -> ids.add(member.id()));
        }
        return ids;
    }

    private String rosterOf(List<GroupResponse> groups) {
        StringBuilder roster = new StringBuilder();
        for (GroupResponse group : groups) {
            roster.append("- 팀 \"").append(group.name()).append("\"");
            roster.append(group.leader() == null
                    ? " · 리더 없음"
                    : " · 리더: %s(id=%d)".formatted(group.leader().name(), group.leader().id()));
            if (!group.members().isEmpty()) {
                roster.append(" · 멤버: ").append(group.members().stream()
                        .map(member -> "%s(id=%d)".formatted(member.name(), member.id()))
                        .collect(Collectors.joining(", ")));
            }
            roster.append("\n");
        }
        return roster.toString();
    }

    private Agent requireAgent(Long agentId) {
        return agentRepository.findByIdWithRelations(agentId)
                .orElseThrow(() -> new NotFoundException("Agent %d not found".formatted(agentId)));
    }

    /** 명령을 받을 루트 에이전트: 지정 대상 → 그룹 리더 → 프로젝트 마스터. */
    private Long resolveRootAgent(Project project, Long targetAgentId, Long groupId) {
        if (targetAgentId != null) {
            return targetAgentId;
        }
        Long leaderId = leaderOf(groupId);
        if (leaderId != null) {
            return leaderId;
        }
        if (project.getMasterAgentId() == null) {
            throw new ConflictException("프로젝트에 마스터 에이전트가 지정되어 있지 않습니다");
        }
        return project.getMasterAgentId();
    }

    /** 그룹에 명령을 보내면 그 팀 리더가 받는다. 리더가 없으면 알려 준다(400). */
    private Long leaderOf(Long groupId) {
        if (groupId == null) {
            return null;
        }
        GroupResponse group = groupService.findOne(groupId);
        if (group.leader() == null) {
            throw new BadRequestException("Group %d has no leader assigned".formatted(groupId));
        }
        return group.leader().id();
    }

    private String agentName(Long agentId) {
        return agentRepository.findById(agentId).map(Agent::getName).orElse("에이전트 " + agentId);
    }

    private static List<Long> append(List<Long> path, Long agentId) {
        List<Long> next = new ArrayList<>(path);
        next.add(agentId);
        return next;
    }

    private static String statusLabel(ExecutionStatus status) {
        return switch (status) {
            case PENDING -> "대기";
            case RUNNING -> "실행 중";
            case WAITING_CHILD -> "하위 대기";
            case WAITING_INPUT -> "입력 대기";
            case SUCCEEDED -> "완료";
            case FAILED -> "실패";
            case CANCELLED -> "취소";
        };
    }

    // ── 스텝 실행 ────────────────────────────────────────────────────────────────

    private ExecutionStatus applyStep(Long executionId, AgentExecutionResult result) {
        ExecutionStatus status = executionService.applyResult(executionId, result);
        if (result.structured() != null) {
            executionService.recordHandoff(executionId, result.structured());
        }
        return status;
    }

    /** 동시 실행 수를 지키며 CLI 한 번을 돌린다. 슬롯은 CLI 가 도는 동안만 잡는다. */
    private AgentExecutionResult step(Long executionId, Agent agent, ExecutionGuard.Target target, String prompt) {
        try {
            cliSlots.acquire();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("실행이 중단되었습니다", ex);
        }
        try {
            return runner.runStep(executionId, agent, target.cwd(), target.systemPrompt(), prompt, maxCostUsd);
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("실행에 실패했습니다: " + ex.getMessage(), ex);
        } finally {
            cliSlots.release();
        }
    }
}
