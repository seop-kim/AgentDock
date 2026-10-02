package com.agent.dock.execution.service;

import com.agent.dock.agent.domain.Agent;
import com.agent.dock.agent.repository.AgentRepository;
import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.domain.ExecutionStatus;
import com.agent.dock.execution.dto.ExecutionFinishedEvent;
import com.agent.dock.execution.repository.ExecutionLogRepository;
import com.agent.dock.execution.repository.ExecutionRepository;
import com.agent.dock.provider.domain.AiProvider;
import com.agent.dock.provider.domain.ProviderKey;
import com.agent.dock.runtime.interfaces.AgentRuntime;
import com.agent.dock.runtime.service.RuntimeRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 중단된 실행 정리: 기동 시 고아 4종 전이 + 루트 Task 동기화, cancel 의 상태 전이와 종료 상태 불변 가드.
 */
@ExtendWith(MockitoExtension.class)
class ExecutionCancelCleanupTest {

    @Mock ExecutionRepository executionRepository;
    @Mock ExecutionLogRepository logRepository;
    @Mock AgentRepository agentRepository;
    @Mock ExecutionGuard guard;
    @Mock ExecutionFactory factory;
    @Mock ExecutionStreamHub streamHub;
    @Mock RuntimeRegistry runtimeRegistry;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock WorktreeService worktreeService;
    @Mock AgentRuntime runtime;
    @InjectMocks ExecutionService service;

    private static Execution execution(Long id, ExecutionStatus status, Long rootId, Long taskId) {
        Execution execution = new Execution();
        execution.setId(id);
        execution.setStatus(status);
        execution.setRootExecutionId(rootId);
        execution.setTaskId(taskId);
        return execution;
    }

    @Test
    void cancelsEveryOrphanStatusAndLeavesFinishedExecutionsAlone() {
        Execution pending = execution(1L, ExecutionStatus.PENDING, null, 100L);
        Execution running = execution(2L, ExecutionStatus.RUNNING, null, null);
        Execution waitingChild = execution(3L, ExecutionStatus.WAITING_CHILD, 1L, null);
        Execution waitingInput = execution(4L, ExecutionStatus.WAITING_INPUT, 1L, null);
        Execution succeeded = execution(5L, ExecutionStatus.SUCCEEDED, null, 200L);
        Execution failed = execution(6L, ExecutionStatus.FAILED, null, 200L);
        Execution cancelled = execution(7L, ExecutionStatus.CANCELLED, null, 200L);
        when(executionRepository.findAll()).thenReturn(
                List.of(pending, running, waitingChild, waitingInput, succeeded, failed, cancelled));
        when(executionRepository.save(any(Execution.class))).thenAnswer(call -> call.getArgument(0));

        int cleaned = service.cleanupOrphanExecutions();

        assertThat(cleaned).isEqualTo(4);
        assertThat(pending.getStatus()).isEqualTo(ExecutionStatus.CANCELLED);
        assertThat(running.getStatus()).isEqualTo(ExecutionStatus.CANCELLED);
        assertThat(waitingChild.getStatus()).isEqualTo(ExecutionStatus.CANCELLED);
        assertThat(waitingInput.getStatus()).isEqualTo(ExecutionStatus.CANCELLED);
        assertThat(pending.getErrorMessage()).isEqualTo("서버 재기동으로 중단됨");
        assertThat(pending.getFinishedAt()).isNotNull();
        assertThat(succeeded.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(failed.getStatus()).isEqualTo(ExecutionStatus.FAILED);
        assertThat(cancelled.getStatus()).isEqualTo(ExecutionStatus.CANCELLED);
        assertThat(succeeded.getErrorMessage()).isNull();
        verify(executionRepository, times(4)).save(any(Execution.class));
    }

    @Test
    void syncsTheTaskOfRootOrphansOnly() {
        Execution rootWithTask = execution(1L, ExecutionStatus.RUNNING, null, 100L);
        Execution rootWithoutTask = execution(2L, ExecutionStatus.PENDING, null, null);
        Execution child = execution(3L, ExecutionStatus.RUNNING, 1L, 100L);
        when(executionRepository.findAll()).thenReturn(List.of(rootWithTask, rootWithoutTask, child));
        when(executionRepository.save(any(Execution.class))).thenAnswer(call -> call.getArgument(0));

        service.cleanupOrphanExecutions();

        verify(eventPublisher).publishEvent(new ExecutionFinishedEvent(1L, 100L, ExecutionStatus.CANCELLED));
        verify(eventPublisher, times(1)).publishEvent(any(ExecutionFinishedEvent.class));
    }

    @Test
    void doesNothingWhenThereIsNoOrphan() {
        when(executionRepository.findAll()).thenReturn(
                List.of(execution(1L, ExecutionStatus.SUCCEEDED, null, 100L)));

        assertThat(service.cleanupOrphanExecutions()).isZero();

        verify(executionRepository, never()).save(any(Execution.class));
        verify(eventPublisher, never()).publishEvent(any(ExecutionFinishedEvent.class));
    }

    @Test
    void cancelTransitionsARunningExecutionAndSyncsItsTask() {
        Execution running = execution(42L, ExecutionStatus.RUNNING, null, 100L);
        running.setAgentId(9L);
        when(executionRepository.findById(42L)).thenReturn(Optional.of(running));
        when(executionRepository.save(any(Execution.class))).thenAnswer(call -> call.getArgument(0));
        stubAgentAndRuntime(9L);

        Map<String, Boolean> result = service.cancel(42L);

        assertThat(result).containsEntry("cancelled", true);
        assertThat(running.getStatus()).isEqualTo(ExecutionStatus.CANCELLED);
        assertThat(running.getErrorMessage()).isEqualTo("사용자가 취소함");
        assertThat(running.getFinishedAt()).isNotNull();
        verify(runtime).cancel("42");
        verify(eventPublisher).publishEvent(new ExecutionFinishedEvent(42L, 100L, ExecutionStatus.CANCELLED));
    }

    @Test
    void cancelOfAChildDoesNotTouchTheTask() {
        Execution child = execution(44L, ExecutionStatus.RUNNING, 1L, 100L);
        child.setAgentId(9L);
        when(executionRepository.findById(44L)).thenReturn(Optional.of(child));
        when(executionRepository.save(any(Execution.class))).thenAnswer(call -> call.getArgument(0));
        stubAgentAndRuntime(9L);

        service.cancel(44L);

        assertThat(child.getStatus()).isEqualTo(ExecutionStatus.CANCELLED);
        verify(eventPublisher, never()).publishEvent(any(ExecutionFinishedEvent.class));
    }

    @Test
    void cancelLeavesAnAlreadyFinishedExecutionUntouched() {
        Execution done = execution(43L, ExecutionStatus.SUCCEEDED, null, 100L);
        done.setAgentId(9L);
        when(executionRepository.findById(43L)).thenReturn(Optional.of(done));
        stubAgentAndRuntime(9L);

        Map<String, Boolean> result = service.cancel(43L);

        assertThat(result).containsEntry("cancelled", false);
        assertThat(done.getStatus()).isEqualTo(ExecutionStatus.SUCCEEDED);
        assertThat(done.getErrorMessage()).isNull();
        verify(executionRepository, never()).save(any(Execution.class));
        verify(eventPublisher, never()).publishEvent(any(ExecutionFinishedEvent.class));
    }

    @Test
    void cancelReportsMissingExecution() {
        when(executionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel(99L)).isInstanceOf(NotFoundException.class);
    }

    private void stubAgentAndRuntime(Long agentId) {
        AiProvider provider = new AiProvider();
        provider.setKey(ProviderKey.CLAUDE_CODE);
        Agent agent = new Agent();
        agent.setId(agentId);
        agent.setProvider(provider);
        when(agentRepository.findByIdWithRelations(agentId)).thenReturn(Optional.of(agent));
        when(runtimeRegistry.resolve("CLAUDE_CODE")).thenReturn(runtime);
    }
}