package com.agent.dock.delegation.service;

import com.agent.dock.agent.domain.Agent;
import com.agent.dock.agent.dto.AgentSummary;
import com.agent.dock.agent.repository.AgentRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.domain.ExecutionDecision;
import com.agent.dock.execution.domain.ExecutionStatus;
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
import com.agent.dock.runtime.dto.ExecutionMetrics;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 계약 finish: 자식 결과가 도착했을 때 **모두 성공**이면 부모 판단(모델 재호출)을 생략하고 곧바로 마무리하고,
 * 하나라도 실패·부분 성공이면 기존처럼 결과를 붙여 다시 판단하는지 고정한다.
 */
@ExtendWith(MockitoExtension.class)
class DelegationFinishTest {

    private static final Long PROJECT = 6L;
    private static final Long ROOT = 100L;
    private static final Long CHILD = 101L;
    private static final Long MASTER = 9L;
    private static final Long WORKER = 10L;
    private static final Long TASK = 7L;
    private static final Long WORKSPACE = 3L;
    private static final String REQUEST = "라이브 체크 파일을 만들어줘";

    @Mock ExecutionService executionService;
    @Mock ExecutionRunner runner;
    @Mock ExecutionStreamHub streamHub;
    @Mock ExecutionFactory executionFactory;
    @Mock ExecutionRepository executionRepository;
    @Mock ExecutionGuard guard;
    @Mock AgentRepository agentRepository;
    @Mock ProjectRepository projectRepository;
    @Mock GroupService groupService;
    @Mock RuleRouter ruleRouter;
    @Mock WorktreeService worktreeService;
    @Mock EventPublisher changeEvents;
    @InjectMocks DelegationService service;

    @TempDir Path cwd;

    private Execution root;
    private Execution child;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxDepth", 3);
        ReflectionTestUtils.setField(service, "maxExecutions", 20);
        ReflectionTestUtils.setField(service, "maxSteps", 4);
        ReflectionTestUtils.setField(service, "maxTargets", 4);
        ReflectionTestUtils.setField(service, "maxRepeatPerAgent", 3);
        ReflectionTestUtils.setField(service, "maxConcurrent", 1);
        ReflectionTestUtils.setField(service, "maxCostUsd", BigDecimal.valueOf(5));
        service.initSlots();
    }

    @Test
    void finishesWithoutRejudgingWhenEveryChildSucceeds() throws Exception {
        givenTree();
        when(runner.runStep(eq(CHILD), any(), any(), any(), any(), any())).thenReturn(workSuccess());
        when(runner.runStep(eq(ROOT), any(), any(), any(), any(), any())).thenReturn(delegateToWorker());
        when(executionService.applyResult(eq(CHILD), any(), anyBoolean())).thenReturn(ExecutionStatus.SUCCEEDED);

        service.start(PROJECT, MASTER, null, REQUEST, TASK);

        // 부모 판단은 한 번만 — 자식이 모두 성공했으므로 재판단(모델 재호출)이 없다.
        verify(runner, timeout(5000).times(1)).runStep(eq(ROOT), any(), any(), any(), any(), any());
        verify(executionService, timeout(5000)).recordDecision(ROOT, ExecutionDecision.DONE, null);
        verify(executionService, timeout(5000)).markSucceeded(ROOT);
        verify(streamHub, timeout(5000)).system(ROOT, "⎿ 자식 결과가 모두 성공해 재판단 없이 마무리합니다");
        // 마무리 요약은 자식 결과에서 모아 남긴다.
        org.mockito.ArgumentCaptor<Map> handoff = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(executionService, timeout(5000).times(2)).recordHandoff(eq(ROOT), handoff.capture());
        assertThat(handoff.getAllValues().stream().map(value -> String.valueOf(value.get("summary"))).toList())
                .anyMatch(summary -> summary.contains("모두 성공"));
        // 트리 결과 되돌리기까지 끝나 워커가 정리됐는지(테스트 종료 경합 방지).
        verify(executionService, timeout(5000)).publishFinished(ROOT, TASK, ExecutionStatus.SUCCEEDED);
    }

    @Test
    void rejudgesWhenAChildFails() throws Exception {
        givenTree();
        when(runner.runStep(eq(CHILD), any(), any(), any(), any(), any())).thenReturn(AgentExecutionResult.failed(1));
        when(runner.runStep(eq(ROOT), any(), any(), any(), any(), any())).thenReturn(delegateToWorker(), doneContract());
        when(executionService.applyResult(eq(CHILD), any(), anyBoolean())).thenReturn(ExecutionStatus.FAILED);

        service.start(PROJECT, MASTER, null, REQUEST, TASK);

        // 실패 자식이 있으면 기존 흐름대로 결과를 붙여 다시 판단한다(부모 판단 2회).
        verify(runner, timeout(5000).times(2)).runStep(eq(ROOT), any(), any(), any(), any(), any());
        verify(executionService, timeout(5000)).markSucceeded(ROOT);
        verify(streamHub, never()).system(ROOT, "⎿ 자식 결과가 모두 성공해 재판단 없이 마무리합니다");
        verify(executionService, timeout(5000)).publishFinished(ROOT, TASK, ExecutionStatus.SUCCEEDED);
    }

    /** 자식 하나가 있는 트리(마스터 9 → 작업자 10)의 최소 문맥과 공통 목 스텁. */
    private void givenTree() {
        Project project = new Project();
        project.setId(PROJECT);
        project.setName("test");
        project.setMasterAgentId(MASTER);

        Agent master = new Agent();
        master.setId(MASTER);
        master.setName("총괄");
        master.setProjectId(PROJECT);

        Agent worker = new Agent();
        worker.setId(WORKER);
        worker.setName("작업자");
        worker.setProjectId(PROJECT);

        GroupResponse group = new GroupResponse(1L, PROJECT, "백엔드 팀", "", "", "",
                new AgentSummary(MASTER, "총괄"), List.of(new AgentSummary(WORKER, "작업자")), null, null, null, null);

        root = new Execution();
        root.setId(ROOT);
        root.setAgentId(MASTER);
        root.setTaskId(TASK);
        root.setPrompt("");
        root.setStatus(ExecutionStatus.PENDING);

        child = new Execution();
        child.setId(CHILD);
        child.setAgentId(WORKER);
        child.setTaskId(TASK);
        child.setParentExecutionId(ROOT);
        child.setRootExecutionId(ROOT);
        child.setStatus(ExecutionStatus.PENDING);
        child.setHandoff(Map.of("summary", "끝냈습니다", "changedFiles", List.of("a.txt")));

        ExecutionGuard.Target rootTarget = new ExecutionGuard.Target(MASTER, WORKSPACE, cwd.toString(), "시스템", null, null);
        ExecutionGuard.Target childTarget = new ExecutionGuard.Target(WORKER, WORKSPACE, cwd.toString(), "시스템", null, null);
        when(projectRepository.findById(PROJECT)).thenReturn(Optional.of(project));
        when(executionService.startRoot(eq(MASTER), eq(PROJECT), isNull(), any(), eq(TASK)))
                .thenReturn(new ExecutionService.Started(root, rootTarget));
        when(worktreeService.create(cwd.toString(), ROOT))
                .thenReturn(new WorktreeService.Worktree(null, null, "저장소 아님"));
        when(groupService.findAll(PROJECT)).thenReturn(List.of(group));
        when(agentRepository.findByIdWithRelations(MASTER)).thenReturn(Optional.of(master));
        when(agentRepository.findByIdWithRelations(WORKER)).thenReturn(Optional.of(worker));
        when(agentRepository.findById(WORKER)).thenReturn(Optional.of(worker));
        when(guard.prepare(eq(WORKER), eq(PROJECT), eq(1L))).thenReturn(childTarget);
        when(executionFactory.createChild(any(), any(), eq(TASK), any(), any())).thenReturn(child);
        when(executionRepository.findByRootExecutionIdOrderByIdAsc(ROOT)).thenReturn(List.of());
        when(executionRepository.countByRootExecutionId(ROOT)).thenReturn(0L);
        when(executionRepository.findById(ROOT)).thenReturn(Optional.of(root));
        when(executionRepository.findById(CHILD)).thenReturn(Optional.of(child));
        when(executionService.applyResult(eq(ROOT), any(), anyBoolean())).thenReturn(ExecutionStatus.RUNNING);
    }

    private AgentExecutionResult delegateToWorker() {
        Map<String, Object> target = Map.of("agentId", WORKER, "prompt", "파일을 만들어 주세요");
        return new AgentExecutionResult(0, "{}", Map.of("action", "delegate", "targets", List.of(target)),
                ExecutionMetrics.empty(), false);
    }

    private AgentExecutionResult doneContract() {
        return new AgentExecutionResult(0, "{}", Map.of("action", "done", "summary", "끝냈습니다"),
                ExecutionMetrics.empty(), false);
    }

    private AgentExecutionResult workSuccess() {
        return new AgentExecutionResult(0, "{\"summary\":\"끝냈습니다\"}",
                Map.of("summary", "끝냈습니다", "changedFiles", List.of("a.txt")),
                ExecutionMetrics.empty(), false);
    }
}