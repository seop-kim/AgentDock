package com.agent.dock.execution;

import com.agent.dock.agent.Agent;
import com.agent.dock.agent.AgentRepository;
import com.agent.dock.common.ConflictException;
import com.agent.dock.permission.PermissionAction;
import com.agent.dock.permission.PermissionProfile;
import com.agent.dock.permission.PermissionService;
import com.agent.dock.project.Project;
import com.agent.dock.project.ProjectRepository;
import com.agent.dock.provider.AiConnection;
import com.agent.dock.provider.AiConnectionRepository;
import com.agent.dock.provider.AiProvider;
import com.agent.dock.provider.ConnectionStatus;
import com.agent.dock.runtime.RuntimeRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
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
    @Mock RuntimeRegistry runtimeRegistry;
    @Mock PermissionService permissionService;
    @Mock AiConnectionRepository connectionRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks ExecutionService service;

    private AiProvider provider;

    @BeforeEach
    void setUp() {
        provider = new AiProvider();
        provider.setId(1L);
        Agent agent = new Agent();
        agent.setProvider(provider);
        agent.setPermissionProfile(new PermissionProfile());
        when(agentRepository.findByIdWithRelations(5L)).thenReturn(Optional.of(agent));
        when(projectRepository.findWithWorkspace(10L)).thenReturn(Optional.of(new Project()));
        when(permissionService.isAllowed(any(), eq(PermissionAction.TERMINAL_EXECUTE))).thenReturn(true);
    }

    @Test
    void rejectsWhenProviderDeleted() {
        provider.setDeletedAt(Instant.now());

        assertThatThrownBy(() -> service.create(5L, 10L, "prompt"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("deleted");
        verify(executionRepository, never()).save(any());
        verifyNoInteractions(runtimeRegistry);
    }

    @Test
    void rejectsWhenNoConnection() {
        when(connectionRepository.findByProviderId(1L)).thenReturn(List.of());

        assertThatThrownBy(() -> service.create(5L, 10L, "prompt"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not CONNECTED");
        verify(executionRepository, never()).save(any());
        verifyNoInteractions(runtimeRegistry);
    }

    @Test
    void rejectsWhenConnectionInError() {
        AiConnection connection = new AiConnection();
        connection.setStatus(ConnectionStatus.ERROR);
        when(connectionRepository.findByProviderId(1L)).thenReturn(List.of(connection));

        assertThatThrownBy(() -> service.create(5L, 10L, "prompt"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("not CONNECTED");
        verify(executionRepository, never()).save(any());
        verifyNoInteractions(runtimeRegistry);
    }
}
