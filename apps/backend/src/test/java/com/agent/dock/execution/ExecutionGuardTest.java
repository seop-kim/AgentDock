package com.agent.dock.execution;

import com.agent.dock.agent.Agent;
import com.agent.dock.agent.AgentRepository;
import com.agent.dock.common.ConflictException;
import com.agent.dock.common.ForbiddenException;
import com.agent.dock.group.AgentGroup;
import com.agent.dock.group.AgentGroupRepository;
import com.agent.dock.permission.PermissionAction;
import com.agent.dock.permission.PermissionProfile;
import com.agent.dock.permission.PermissionService;
import com.agent.dock.project.Project;
import com.agent.dock.project.ProjectRepository;
import com.agent.dock.project.ProjectService;
import com.agent.dock.provider.AiProvider;
import com.agent.dock.provider.ConnectionStatus;
import com.agent.dock.workspace.Workspace;
import com.agent.dock.workspace.WorkspaceRuntimeStatus;
import com.agent.dock.workspace.WorkspaceRuntimeStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 실행 전 불변 규칙은 Runtime 호출 전에 막아야 한다(403/409).
 */
@ExtendWith(MockitoExtension.class)
class ExecutionGuardTest {
    @Mock ExecutionRepository executionRepository;
    @Mock AgentRepository agentRepository;
    @Mock ProjectRepository projectRepository;
    @Mock ProjectService projectService;
    @Mock PermissionService permissionService;
    @Mock WorkspaceRuntimeStatusRepository runtimeStatusRepository;
    @Mock AgentGroupRepository groupRepository;
    @InjectMocks ExecutionGuard guard;

    private AiProvider provider;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        provider = new AiProvider();
        provider.setId(1L);
        provider.setEnabled(true);
        workspace = new Workspace();
        workspace.setId(3L);
        workspace.setPath("C:\\Temp");

        Agent agent = new Agent();
        agent.setId(5L);
        agent.setProvider(provider);
        agent.setPermissionProfile(new PermissionProfile());
        agent.setPersona("내 역할");

        Project project = new Project();
        project.setId(10L);
        project.setMasterPrompt("마스터 규칙");

        when(agentRepository.findByIdWithRelations(5L)).thenReturn(Optional.of(agent));
        when(projectRepository.findById(10L)).thenReturn(Optional.of(project));
    }

    /** 권한이 허용되고 기본 워크스페이스가 있는 정상 경로. */
    private void givenRunnable() {
        when(permissionService.isAllowed(any(), eq(PermissionAction.TERMINAL_EXECUTE))).thenReturn(true);
        when(projectService.defaultWorkspace(10L)).thenReturn(workspace);
    }

    private void givenWorkspaceStatus(ConnectionStatus status) {
        WorkspaceRuntimeStatus runtimeStatus = new WorkspaceRuntimeStatus();
        runtimeStatus.setStatus(status);
        when(runtimeStatusRepository.findByWorkspaceIdAndProviderId(3L, 1L)).thenReturn(Optional.of(runtimeStatus));
    }

    @Test
    void rejectsWhenPermissionDoesNotAllowTerminalExecute() {
        // 권한이 거부되면 워크스페이스 조회 전에 막힌다(isAllowed 의 기본값이 false 다).
        assertThatThrownBy(() -> guard.prepare(5L, 10L, null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("TERMINAL_EXECUTE");
        verifyNoInteractions(executionRepository);
    }

    @Test
    void rejectsWhenRuntimeDeleted() {
        givenRunnable();
        provider.setDeletedAt(Instant.now());

        assertThatThrownBy(() -> guard.prepare(5L, 10L, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("deleted");
        verifyNoInteractions(executionRepository);
    }

    @Test
    void rejectsWhenRuntimeDisabled() {
        givenRunnable();
        provider.setEnabled(false);

        assertThatThrownBy(() -> guard.prepare(5L, 10L, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("turned off");
        verifyNoInteractions(executionRepository);
    }

    @Test
    void rejectsWhenProjectHasNoWorkspace() {
        when(permissionService.isAllowed(any(), eq(PermissionAction.TERMINAL_EXECUTE))).thenReturn(true);
        when(projectService.defaultWorkspace(10L))
                .thenThrow(new ConflictException("Project 10 has no workspace assigned"));

        assertThatThrownBy(() -> guard.prepare(5L, 10L, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no workspace");
        verifyNoInteractions(executionRepository);
    }

    @Test
    void rejectsWhenWorkspaceHasNoRuntimeCheck() {
        givenRunnable();

        assertThatThrownBy(() -> guard.prepare(5L, 10L, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not verified");
        verifyNoInteractions(executionRepository);
    }

    @Test
    void rejectsWhenWorkspaceRuntimeFailed() {
        givenRunnable();
        givenWorkspaceStatus(ConnectionStatus.ERROR);

        assertThatThrownBy(() -> guard.prepare(5L, 10L, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not verified");
        verifyNoInteractions(executionRepository);
    }

    @Test
    void combinesPromptLayersFromProjectGroupAndAgent() {
        givenRunnable();
        givenWorkspaceStatus(ConnectionStatus.CONNECTED);
        AgentGroup group = new AgentGroup();
        group.setId(7L);
        group.setPrompt("그룹 규칙");
        when(groupRepository.findById(7L)).thenReturn(Optional.of(group));

        ExecutionGuard.Target target = guard.prepare(5L, 10L, 7L);

        assertThat(target.systemPrompt()).isEqualTo("마스터 규칙\n\n그룹 규칙\n\n내 역할");
        assertThat(target.workspacePath()).isEqualTo("C:\\Temp");
        assertThat(target.agentId()).isEqualTo(5L);
    }

    @Test
    void skipsGroupLayerWhenNotRunThroughGroup() {
        givenRunnable();
        givenWorkspaceStatus(ConnectionStatus.CONNECTED);

        ExecutionGuard.Target target = guard.prepare(5L, 10L, null);

        assertThat(target.systemPrompt()).isEqualTo("마스터 규칙\n\n내 역할");
    }
}
