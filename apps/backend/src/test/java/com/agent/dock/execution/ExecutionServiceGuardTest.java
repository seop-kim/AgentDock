package com.agent.dock.execution;

import com.agent.dock.agent.Agent;
import com.agent.dock.agent.AgentRepository;
import com.agent.dock.common.ConflictException;
import com.agent.dock.permission.PermissionAction;
import com.agent.dock.permission.PermissionProfile;
import com.agent.dock.permission.PermissionService;
import com.agent.dock.project.Project;
import com.agent.dock.project.ProjectRepository;
import com.agent.dock.project.ProjectService;
import com.agent.dock.provider.AiProvider;
import com.agent.dock.provider.ConnectionStatus;
import com.agent.dock.runtime.RuntimeRegistry;
import com.agent.dock.workspace.Workspace;
import com.agent.dock.workspace.WorkspaceRuntimeStatus;
import com.agent.dock.workspace.WorkspaceRuntimeStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionServiceGuardTest {
    @Mock ExecutionRepository executionRepository;
    @Mock ExecutionLogRepository logRepository;
    @Mock AgentRepository agentRepository;
    @Mock ProjectRepository projectRepository;
    @Mock ProjectService projectService;
    @Mock RuntimeRegistry runtimeRegistry;
    @Mock PermissionService permissionService;
    @Mock WorkspaceRuntimeStatusRepository runtimeStatusRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks ExecutionService service;

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
        agent.setProvider(provider);
        agent.setPermissionProfile(new PermissionProfile());
        when(agentRepository.findByIdWithRelations(5L)).thenReturn(Optional.of(agent));
        when(projectRepository.findById(10L)).thenReturn(Optional.of(new Project()));
        when(permissionService.isAllowed(any(), eq(PermissionAction.TERMINAL_EXECUTE))).thenReturn(true);
        when(projectService.defaultWorkspace(10L)).thenReturn(workspace);
    }

    private void givenWorkspaceStatus(ConnectionStatus status) {
        WorkspaceRuntimeStatus runtimeStatus = new WorkspaceRuntimeStatus();
        runtimeStatus.setStatus(status);
        when(runtimeStatusRepository.findByWorkspaceIdAndProviderId(3L, 1L)).thenReturn(Optional.of(runtimeStatus));
    }

    @Test
    void rejectsWhenRuntimeDeleted() {
        provider.setDeletedAt(Instant.now());

        assertThatThrownBy(() -> service.create(5L, 10L, "prompt"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("deleted");
        verify(executionRepository, never()).save(any());
        verifyNoInteractions(runtimeRegistry);
    }

    @Test
    void rejectsWhenRuntimeDisabled() {
        provider.setEnabled(false);

        assertThatThrownBy(() -> service.create(5L, 10L, "prompt"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("turned off");
        verify(executionRepository, never()).save(any());
        verifyNoInteractions(runtimeRegistry);
    }

    @Test
    void rejectsWhenProjectHasNoWorkspace() {
        when(projectService.defaultWorkspace(10L))
                .thenThrow(new ConflictException("Project 10 has no workspace assigned"));

        assertThatThrownBy(() -> service.create(5L, 10L, "prompt"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("no workspace");
        verify(executionRepository, never()).save(any());
        verifyNoInteractions(runtimeRegistry);
    }

    @Test
    void rejectsWhenWorkspaceHasNoRuntimeCheck() {
        assertThatThrownBy(() -> service.create(5L, 10L, "prompt"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not verified");
        verify(executionRepository, never()).save(any());
        verifyNoInteractions(runtimeRegistry);
    }

    @Test
    void rejectsWhenWorkspaceRuntimeFailed() {
        givenWorkspaceStatus(ConnectionStatus.ERROR);

        assertThatThrownBy(() -> service.create(5L, 10L, "prompt"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not verified");
        verify(executionRepository, never()).save(any());
        verifyNoInteractions(runtimeRegistry);
    }
}
