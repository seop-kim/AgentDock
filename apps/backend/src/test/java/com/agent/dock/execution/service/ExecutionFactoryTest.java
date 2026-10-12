package com.agent.dock.execution.service;

import com.agent.dock.agent.domain.Agent;
import com.agent.dock.agent.repository.AgentRepository;
import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.repository.ExecutionRepository;
import com.agent.dock.workspace.domain.Workspace;
import com.agent.dock.workspace.repository.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 트리 격리 규칙: 루트가 만든 worktree 를 자식이 그대로 물려받아 같은 디렉터리에서 돈다(부모의 편집을 봐야 한다).
 */
@ExtendWith(MockitoExtension.class)
class ExecutionFactoryTest {

    @Mock ExecutionRepository executionRepository;
    @Mock AgentRepository agentRepository;
    @Mock WorkspaceRepository workspaceRepository;
    @InjectMocks ExecutionFactory factory;

    @Test
    void childInheritsTheWorktreeOfItsRoot() {
        Agent agent = new Agent();
        agent.setId(9L);
        Workspace workspace = new Workspace();
        workspace.setId(3L);
        Execution root = new Execution();
        root.setId(42L);

        when(agentRepository.getReferenceById(9L)).thenReturn(agent);
        when(workspaceRepository.getReferenceById(3L)).thenReturn(workspace);
        when(executionRepository.getReferenceById(42L)).thenReturn(root);
        when(executionRepository.save(any(Execution.class))).thenAnswer(call -> call.getArgument(0));

        ExecutionGuard.Target childTarget = new ExecutionGuard.Target(9L, 3L, "C:\\repo", "시스템", null, null)
                .withWorktree("C:\\repo-wt\\42", "agentdock/exec-42");

        Execution child = factory.createChild(childTarget, "자식 지시", 5L, root, root);

        assertThat(child.getWorktreePath()).isEqualTo("C:\\repo-wt\\42");
        assertThat(child.getWorktreeBranch()).isEqualTo("agentdock/exec-42");
        assertThat(childTarget.cwd()).isEqualTo("C:\\repo-wt\\42");
    }

    @Test
    void runsInTheWorkspaceWhenThereIsNoWorktree() {
        ExecutionGuard.Target target = new ExecutionGuard.Target(9L, 3L, "C:\\repo", "시스템", null, null);

        assertThat(target.cwd()).isEqualTo("C:\\repo");
        assertThat(target.worktreePath()).isNull();
    }
}
