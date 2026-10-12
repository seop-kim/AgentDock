package com.agent.dock.execution.service;

import com.agent.dock.agent.repository.AgentRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.domain.ChangedFile;
import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.domain.ExecutionStatus;
import com.agent.dock.execution.domain.MergeStatus;
import com.agent.dock.execution.repository.ExecutionLogRepository;
import com.agent.dock.execution.repository.ExecutionRepository;
import com.agent.dock.runtime.service.RuntimeRegistry;
import com.agent.dock.settings.dto.MergePolicyResponse;
import com.agent.dock.settings.service.MergePolicyService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionMergePolicyCleanupTest {

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
    @Mock MergePolicyService mergePolicyService;
    @InjectMocks ExecutionService service;
    @Test
    void cleansUpTheWorktreeAfterMergeWhenPolicyEnabled() {
        Execution root = mergedRoot();
        when(mergePolicyService.get()).thenReturn(new MergePolicyResponse(true, true, Instant.now()));
        when(worktreeService.remove("C:\\repo-wt\\42", "agentdock/exec-42", true))
                .thenReturn(new WorktreeService.Removal(true, null));

        service.recordTreeResult(42L, MergeStatus.MERGED, "abc1234", null, List.of(new ChangedFile("A", "a.txt")));

        verify(worktreeService).remove("C:\\repo-wt\\42", "agentdock/exec-42", true);
        assertThat(root.getMergeStatus()).isEqualTo(MergeStatus.MERGED);
        assertThat(root.getWorktreePath()).isNull();
        assertThat(root.getWorktreeBranch()).isNull();
    }

    @Test
    void keepsTheWorktreeWhenPolicyDisabled() {
        Execution root = mergedRoot();
        when(mergePolicyService.get()).thenReturn(new MergePolicyResponse(false, true, Instant.now()));

        service.recordTreeResult(42L, MergeStatus.MERGED, "abc1234", null, List.of());

        verifyNoInteractions(worktreeService);
        assertThat(root.getWorktreePath()).isEqualTo("C:\\repo-wt\\42");
    }
    @Test
    void doesNotCleanUpWhenMergeIsManual() {
        Execution root = mergedRoot();

        service.recordTreeResult(42L, MergeStatus.MANUAL, null, "충돌", List.of());

        verifyNoInteractions(worktreeService);
        verifyNoInteractions(mergePolicyService);
        assertThat(root.getMergeStatus()).isEqualTo(MergeStatus.MANUAL);
    }

    @Test
    void stillRecordsTheResultWhenCleanupFails() {
        Execution root = mergedRoot();
        when(mergePolicyService.get()).thenReturn(new MergePolicyResponse(true, true, Instant.now()));
        when(worktreeService.remove("C:\\repo-wt\\42", "agentdock/exec-42", true))
                .thenReturn(new WorktreeService.Removal(false, "저장소를 찾지 못했습니다"));

        service.recordTreeResult(42L, MergeStatus.MERGED, "abc1234", null, List.of());

        assertThat(root.getMergeStatus()).isEqualTo(MergeStatus.MERGED);
        assertThat(root.getWorktreePath()).isEqualTo("C:\\repo-wt\\42");
    }

    private Execution mergedRoot() {
        Execution root = new Execution();
        root.setId(42L);
        root.setStatus(ExecutionStatus.SUCCEEDED);
        root.setWorktreePath("C:\\repo-wt\\42");
        root.setWorktreeBranch("agentdock/exec-42");
        when(executionRepository.findById(42L)).thenReturn(Optional.of(root));
        when(executionRepository.save(any(Execution.class))).thenAnswer(call -> call.getArgument(0));
        return root;
    }
}