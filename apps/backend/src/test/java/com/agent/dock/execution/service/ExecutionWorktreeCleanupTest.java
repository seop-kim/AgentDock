package com.agent.dock.execution.service;

import com.agent.dock.agent.repository.AgentRepository;
import com.agent.dock.common.exception.ConflictException;
import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.domain.ExecutionStatus;
import com.agent.dock.execution.repository.ExecutionLogRepository;
import com.agent.dock.execution.repository.ExecutionRepository;
import com.agent.dock.runtime.service.RuntimeRegistry;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 워크트리 정리 규칙: 아직 도는 트리는 막고(409), 끝난 트리는 지운 뒤 실행에서 경로·브랜치를 비운다.
 */
@ExtendWith(MockitoExtension.class)
class ExecutionWorktreeCleanupTest {

    @Mock ExecutionRepository executionRepository;
    @Mock ExecutionLogRepository logRepository;
    @Mock AgentRepository agentRepository;
    @Mock ExecutionGuard guard;
    @Mock ExecutionFactory factory;
    @Mock ExecutionStreamHub streamHub;
    @Mock RuntimeRegistry runtimeRegistry;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock WorktreeService worktreeService;
    @InjectMocks ExecutionService service;

    @Test
    void refusesWhileTheExecutionIsStillRunning() {
        Execution running = execution(42L, ExecutionStatus.RUNNING, "C:\\repo-wt\\42");
        when(executionRepository.findById(42L)).thenReturn(Optional.of(running));

        assertThatThrownBy(() -> service.removeWorktree(42L, true))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("실행 중");
        verifyNoInteractions(worktreeService);
    }

    @Test
    void removesTheWorktreeOfAFinishedExecutionAndClearsIt() {
        Execution finished = execution(42L, ExecutionStatus.SUCCEEDED, "C:\\repo-wt\\42");
        finished.setWorktreeBranch("agentdock/exec-42");
        when(executionRepository.findById(42L)).thenReturn(Optional.of(finished));
        when(worktreeService.remove("C:\\repo-wt\\42", "agentdock/exec-42", true))
                .thenReturn(new WorktreeService.Removal(true, null));
        when(executionRepository.save(any(Execution.class))).thenAnswer(call -> call.getArgument(0));

        service.removeWorktree(42L, true);

        verify(worktreeService).remove("C:\\repo-wt\\42", "agentdock/exec-42", true);
        assertThat(finished.getWorktreePath()).isNull();
        assertThat(finished.getWorktreeBranch()).isNull();
    }

    @Test
    void reportsMissingWorktreeWhenTheExecutionWasNotIsolated() {
        Execution isolated = execution(43L, ExecutionStatus.SUCCEEDED, null);
        when(executionRepository.findById(43L)).thenReturn(Optional.of(isolated));

        assertThatThrownBy(() -> service.removeWorktree(43L, false))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("no worktree");
        verifyNoInteractions(worktreeService);
    }

    private static Execution execution(Long id, ExecutionStatus status, String worktreePath) {
        Execution execution = new Execution();
        execution.setId(id);
        execution.setStatus(status);
        execution.setWorktreePath(worktreePath);
        return execution;
    }
}
