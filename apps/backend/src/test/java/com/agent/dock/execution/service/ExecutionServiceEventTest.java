package com.agent.dock.execution.service;

import com.agent.dock.agent.domain.Agent;
import com.agent.dock.agent.repository.AgentRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.domain.ExecutionStatus;
import com.agent.dock.execution.repository.ExecutionLogRepository;
import com.agent.dock.execution.repository.ExecutionRepository;
import com.agent.dock.runtime.dto.AgentExecutionResult;
import com.agent.dock.runtime.service.RuntimeRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 실행 상태가 바뀌면 전역 스트림에 `execution.changed` 가 나간다 — 화면이 폴링 대신 이 알림으로 실행 트리를 다시 읽는다.
 * 실행 에이전트의 프로젝트 id 를 함께 실어 보낸다(어느 프로젝트를 다시 읽을지 화면이 고른다).
 */
@ExtendWith(MockitoExtension.class)
class ExecutionServiceEventTest {

    @Mock ExecutionRepository executionRepository;
    @Mock ExecutionLogRepository logRepository;
    @Mock AgentRepository agentRepository;
    @Mock ExecutionGuard guard;
    @Mock ExecutionFactory factory;
    @Mock ExecutionStreamHub streamHub;
    @Mock RuntimeRegistry runtimeRegistry;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock WorktreeService worktreeService;
    @Mock EventPublisher changeEvents;
    @InjectMocks ExecutionService service;

    @Test
    void publishesExecutionChangedWhenTheQuestionBlocksTheRun() {
        givenExecution(42L, 9L, 7L);

        service.markWaitingInput(42L, "어느 쪽으로 할까요?", List.of("A", "B"));

        verify(changeEvents).executionChanged(6L, 42L, 7L);
    }

    @Test
    void publishesExecutionChangedWhenTheAnswerResumesTheRun() {
        givenExecution(42L, 9L, 7L);

        service.recordAnswer(42L, "A 로 해 주세요");

        assertThat(executionRepository.findById(42L).orElseThrow().getAnswer()).isEqualTo("A 로 해 주세요");
        verify(changeEvents).executionChanged(6L, 42L, 7L);
    }

    @Test
    void publishesExecutionChangedWhenTheStepResultChangesTheStatus() {
        Execution execution = givenExecution(42L, 9L, 7L);

        service.applyResult(42L, AgentExecutionResult.failed(1));

        assertThat(execution.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        verify(changeEvents).executionChanged(6L, 42L, 7L);
    }

    private Execution givenExecution(Long executionId, Long agentId, Long taskId) {
        Execution execution = new Execution();
        execution.setId(executionId);
        execution.setAgentId(agentId);
        execution.setTaskId(taskId);
        when(executionRepository.findById(executionId)).thenReturn(Optional.of(execution));
        Agent agent = new Agent();
        agent.setProjectId(6L);
        when(agentRepository.findById(agentId)).thenReturn(Optional.of(agent));
        return execution;
    }
}
