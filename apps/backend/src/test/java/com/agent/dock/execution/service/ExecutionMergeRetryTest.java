package com.agent.dock.execution.service;

import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.agent.repository.AgentRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.common.exception.ConflictException;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.domain.ChangedFile;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.domain.Execution;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.domain.ExecutionStatus;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.domain.MergeStatus;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.dto.ExecutionResponse;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.repository.ExecutionLogRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.repository.ExecutionRepository;
import com.agent.dock.event.service.EventPublisher;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 다시 병합(`POST /executions/{id}/merge`)의 가드와 기록: 도는 트리는 409, 워크트리 브랜치가 없으면 404,
 * 성공하면 실행의 병합 상태·커밋·변경 파일이 갱신되고 SYSTEM 로그가 남는다.
 */
@ExtendWith(MockitoExtension.class)
class ExecutionMergeRetryTest {

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
    void refusesWhileTheTreeIsStillRunning() {
        Execution running = execution(42L, ExecutionStatus.RUNNING, "agentdock/exec-42");
        when(executionRepository.findById(42L)).thenReturn(Optional.of(running));

        assertThatThrownBy(() -> service.retryMerge(42L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("실행 중");
        verifyNoInteractions(worktreeService);
    }

    @Test
    void refusesWhileTheTreeWaitsForAPerson() {
        Execution waiting = execution(42L, ExecutionStatus.WAITING_INPUT, "agentdock/exec-42");
        when(executionRepository.findById(42L)).thenReturn(Optional.of(waiting));

        assertThatThrownBy(() -> service.retryMerge(42L)).isInstanceOf(ConflictException.class);
        verifyNoInteractions(worktreeService);
    }

    @Test
    void reportsMissingBranchWhenTheTreeWasNotIsolated() {
        Execution isolated = execution(43L, ExecutionStatus.SUCCEEDED, null);
        when(executionRepository.findById(43L)).thenReturn(Optional.of(isolated));

        assertThatThrownBy(() -> service.retryMerge(43L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("no worktree branch");
        verifyNoInteractions(worktreeService);
    }

    @Test
    void reportsMissingExecution() {
        when(executionRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.retryMerge(99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void recordsTheRefreshedMergeResultAndLogsIt() {
        Execution manual = execution(42L, ExecutionStatus.SUCCEEDED, "agentdock/exec-42");
        manual.setMergeStatus(MergeStatus.MANUAL);
        manual.setMergeDetail("메인 저장소에 커밋되지 않은 변경이 있습니다: M file.txt");
        manual.setChangedFiles(List.of(new ChangedFile("M", "file.txt")));
        when(executionRepository.findById(42L)).thenReturn(Optional.of(manual));
        when(executionRepository.save(any(Execution.class))).thenAnswer(call -> call.getArgument(0));
        when(worktreeService.mergeBranch("C:\\repo-wt\\42", "agentdock/exec-42"))
                .thenReturn(new WorktreeService.TreeResult(MergeStatus.MERGED, "abc1234", null, "dev",
                        List.of(new ChangedFile("M", "file.txt"))));

        ExecutionResponse response = service.retryMerge(42L);

        assertThat(response.mergeStatus()).isEqualTo(MergeStatus.MERGED);
        assertThat(response.resultCommit()).isEqualTo("abc1234");
        assertThat(response.mergeDetail()).isNull();
        assertThat(response.changedFiles()).containsExactly(new ChangedFile("M", "file.txt"));
        verify(streamHub).system(42L, "⎿ 트리 브랜치 agentdock/exec-42 를 다시 병합합니다");
        verify(streamHub).system(42L, "⎿ 메인 저장소(dev)로 병합했습니다");
    }

    @Test
    void keepsThePreviousChangedFilesWhenTheBranchIsAlreadyMerged() {
        Execution manual = execution(42L, ExecutionStatus.SUCCEEDED, "agentdock/exec-42");
        manual.setChangedFiles(List.of(new ChangedFile("A", "AGENTDOCK_LIVE_CHECK.md")));
        when(executionRepository.findById(42L)).thenReturn(Optional.of(manual));
        when(executionRepository.save(any(Execution.class))).thenAnswer(call -> call.getArgument(0));
        // 이미 병합된 브랜치를 다시 부르면 병합할 것이 없어 변경 파일 diff 가 비어 온다.
        when(worktreeService.mergeBranch("C:\\repo-wt\\42", "agentdock/exec-42"))
                .thenReturn(new WorktreeService.TreeResult(MergeStatus.MERGED, "abc1234", null, "dev", List.of()));

        ExecutionResponse response = service.retryMerge(42L);

        assertThat(response.changedFiles()).containsExactly(new ChangedFile("A", "AGENTDOCK_LIVE_CHECK.md"));
        verify(streamHub, never()).system(eq(42L), contains("자동 병합하지 못했습니다"));
    }

    private static Execution execution(Long id, ExecutionStatus status, String branch) {
        Execution execution = new Execution();
        execution.setId(id);
        execution.setStatus(status);
        execution.setWorktreePath(branch == null ? null : "C:\\repo-wt\\" + id);
        execution.setWorktreeBranch(branch);
        return execution;
    }
}
