package com.agent.dock.delegation.service;

import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.agent.domain.Agent;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.agent.repository.AgentRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.common.exception.ConflictException;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.domain.Execution;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.domain.ExecutionStatus;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.repository.ExecutionRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.service.ExecutionFactory;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.service.ExecutionGuard;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.service.ExecutionRunner;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.service.ExecutionService;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.service.ExecutionStreamHub;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.service.WorktreeService;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.group.service.GroupService;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.project.domain.Project;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.project.repository.ProjectRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.runtime.dto.AgentExecutionResult;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.runtime.dto.ExecutionMetrics;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import static org.mockito.ArgumentMatchers.anyBoolean;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 사람에게 묻고(`ask`) 답을 받아 이어서 도는 흐름: 입력 대기가 아니면 409, 없는 실행이면 404.
 * 답을 넣으면 저장하고 **같은 실행의 판단 루프를 이어서** 돌린다(질문·답이 다음 스텝 프롬프트에 붙는다).
 * 다시 물으면 다시 멈춘다(완료로 남기지 않는다). 스텝 루프는 워커 스레드에서 이어지므로 timeout 으로 기다린다.
 */
@ExtendWith(MockitoExtension.class)
class DelegationAnswerResumeTest {

    /** 판단 상한은 application.yaml 값이다 — 목 객체 주입으로는 채워지지 않아 직접 넣는다. */
    private static final int MAX_STEPS = 4;

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

    /** 이 실행의 작업 디렉터리(cwd). 지시·문맥 파일이 여기 `.agentdock/prompts` 에 쓰인다. */
    @TempDir Path cwd;

    private Execution asked;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "maxDepth", 3);
        ReflectionTestUtils.setField(service, "maxExecutions", 20);
        ReflectionTestUtils.setField(service, "maxSteps", MAX_STEPS);
        ReflectionTestUtils.setField(service, "maxTargets", 4);
        ReflectionTestUtils.setField(service, "maxConcurrent", 1);
        ReflectionTestUtils.setField(service, "maxCostUsd", BigDecimal.valueOf(5));
        // @PostConstruct 는 목 주입에서 불리지 않는다 — CLI 슬롯을 직접 초기화한다.
        service.initSlots();

        asked = new Execution();
        asked.setId(42L);
        asked.setAgentId(9L);
        asked.setTaskId(7L);
        asked.setPrompt("라이브 체크 파일을 만들어줘");
        asked.setStatus(ExecutionStatus.WAITING_INPUT);
        asked.setQuestion("새 파일 이름을 무엇으로 할까요?");
    }

    @Test
    void continuesTheStepLoopAndCarriesTheAnswerIntoTheNextPrompt() throws Exception {
        givenWaitingExecution();
        givenStepReturns(Map.of("action", "done", "summary", "끝냈습니다"));

        service.answer(42L, "  AGENTDOCK_LIVE_CHECK.md  ");

        // 답을 저장하고 로그를 남긴다(앞뒤 공백은 다듬는다).
        verify(executionService).recordAnswer(42L, "AGENTDOCK_LIVE_CHECK.md");
        verify(streamHub).system(42L, "⎿ 답변: AGENTDOCK_LIVE_CHECK.md");
        // 이어서 돈 스텝은 질문·답을 프롬프트에 인라인하지 않고 문맥 파일(`<실행id>-step<N>.md`)로 넘긴다.
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        verify(runner, timeout(5000)).runStep(eq(42L), any(), eq(cwd.toString()), eq("시스템"), prompt.capture(), any());
        assertThat(prompt.getValue()).contains(".agentdock/prompts/42-step0.md");
        assertThat(Files.readString(cwd.resolve(".agentdock/prompts/42-step0.md")))
                .contains("새 파일 이름을 무엇으로 할까요?").contains("AGENTDOCK_LIVE_CHECK.md");
        // 판단이 끝나면 실행을 완료로 남긴다.
        verify(executionService, timeout(5000)).markSucceeded(42L);
    }

    @Test
    void parksAgainWhenTheResumedStepAsksAnotherQuestion() throws Exception {
        givenWaitingExecution();
        givenStepReturns(Map.of("action", "ask", "question", "그럼 기존 파일을 고칠까요?"));

        service.answer(42L, "새 파일로 만들어 주세요");

        verify(executionService, timeout(5000)).markWaitingInput(42L, "그럼 기존 파일을 고칠까요?", List.of());
        verify(streamHub, timeout(5000)).system(42L, "⎿ 질문: 그럼 기존 파일을 고칠까요?");
        // 사람의 답을 기다리는 중이므로 완료로 남기지 않는다.
        verify(executionService, never()).markSucceeded(anyLong());
    }

    @Test
    void rejectsAnAnswerWhenTheExecutionIsNotWaitingForInput() throws Exception {
        asked.setStatus(ExecutionStatus.RUNNING);
        when(executionService.require(42L)).thenReturn(asked);

        assertThatThrownBy(() -> service.answer(42L, "그렇게 해주세요"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("입력 대기");
        verify(runner, never()).runStep(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void reportsMissingExecution() {
        when(executionService.require(99L)).thenThrow(new NotFoundException("Execution 99 not found"));

        assertThatThrownBy(() -> service.answer(99L, "그렇게 해주세요")).isInstanceOf(NotFoundException.class);
    }

    /** 입력 대기 중인 루트 실행(마스터)이 이어서 돌 수 있는 최소 문맥. */
    private void givenWaitingExecution() {
        Agent agent = new Agent();
        agent.setId(9L);
        agent.setName("총괄");
        agent.setProjectId(6L);

        Project project = new Project();
        project.setId(6L);
        project.setName("test");
        project.setMasterAgentId(9L);

        when(executionService.require(42L)).thenReturn(asked);
        when(agentRepository.findByIdWithRelations(9L)).thenReturn(Optional.of(agent));
        when(projectRepository.findById(6L)).thenReturn(Optional.of(project));
        when(groupService.findAll(6L)).thenReturn(List.of());
        when(guard.prepare(9L, 6L, null))
                .thenReturn(new ExecutionGuard.Target(9L, 3L, cwd.toString(), "시스템", null, null));
    }

    /** CLI 한 스텝이 이 계약 JSON 을 냈다고 둔다. */
    private void givenStepReturns(Map<String, Object> structured) throws Exception {
        // 판단 루프의 중간 스텝은 finish=false 로 부른다(실행을 완료로 덮지 않는다) — 3인자 오버로드를 스텁한다.
        when(executionService.applyResult(eq(42L), any(), anyBoolean())).thenReturn(ExecutionStatus.SUCCEEDED);
        when(runner.runStep(eq(42L), any(), any(), any(), any(), any()))
                .thenReturn(new AgentExecutionResult(0, "{}", structured, ExecutionMetrics.empty(), false));
    }
}
