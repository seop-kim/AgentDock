package com.agent.dock.agent;

import com.agent.dock.common.ConflictException;
import com.agent.dock.common.NotFoundException;
import com.agent.dock.permission.PermissionProfile;
import com.agent.dock.permission.PermissionProfileRepository;
import com.agent.dock.project.Project;
import com.agent.dock.project.ProjectWorkspace;
import com.agent.dock.project.ProjectWorkspaceRepository;
import com.agent.dock.project.ProjectRepository;
import com.agent.dock.provider.AiConnectionRepository;
import com.agent.dock.provider.AiProvider;
import com.agent.dock.provider.AiProviderRepository;
import com.agent.dock.provider.ConnectionStatus;
import com.agent.dock.provider.ProviderKey;
import com.agent.dock.role.AgentRole;
import com.agent.dock.role.AgentRoleRepository;
import com.agent.dock.workspace.Workspace;
import com.agent.dock.workspace.WorkspaceRuntimeStatus;
import com.agent.dock.workspace.WorkspaceRuntimeStatusRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {
    @Mock AgentRepository agentRepository;
    @Mock AgentRoleRepository roleRepository;
    @Mock PermissionProfileRepository permissionProfileRepository;
    @Mock AiProviderRepository providerRepository;
    @Mock AiConnectionRepository connectionRepository;
    @Mock ProjectRepository projectRepository;
    @Mock ProjectWorkspaceRepository projectWorkspaceRepository;
    @Mock WorkspaceRuntimeStatusRepository runtimeStatusRepository;
    @InjectMocks AgentService service;

    private AiProvider provider(long id, boolean deleted, boolean enabled) {
        AiProvider provider = new AiProvider();
        provider.setId(id);
        provider.setKey(ProviderKey.CLAUDE_CODE);
        provider.setName("Claude Code");
        provider.setDeletedAt(deleted ? Instant.now() : null);
        provider.setEnabled(enabled);
        return provider;
    }

    private Project project(long id) {
        Project project = new Project();
        project.setId(id);
        project.setName("P" + id);
        return project;
    }

    private Workspace workspace(long id) {
        Workspace workspace = new Workspace();
        workspace.setId(id);
        workspace.setName("W" + id);
        workspace.setPath("C:\\Temp\\W" + id);
        return workspace;
    }

    private Agent agentWith(AiProvider provider) {
        Agent agent = new Agent();
        agent.setId(1L);
        agent.setName("Dev A");
        agent.setProject(project(2L));
        agent.setRole(new AgentRole());
        agent.setPermissionProfile(new PermissionProfile());
        agent.setProvider(provider);
        return agent;
    }

    private ProjectWorkspace link(Project project, Workspace workspace, boolean isDefault) {
        ProjectWorkspace link = new ProjectWorkspace();
        link.setId(9L);
        link.setProject(project);
        link.setWorkspace(workspace);
        link.setDefault(isDefault);
        return link;
    }

    private WorkspaceRuntimeStatus runtimeStatus(Workspace workspace, AiProvider provider, ConnectionStatus status) {
        WorkspaceRuntimeStatus runtimeStatus = new WorkspaceRuntimeStatus();
        runtimeStatus.setWorkspace(workspace);
        runtimeStatus.setProvider(provider);
        runtimeStatus.setStatus(status);
        return runtimeStatus;
    }

    @Test
    void findAllMarksAgentOfDeletedRuntimeUnavailable() {
        when(agentRepository.findAllWithRelations()).thenReturn(List.of(agentWith(provider(7L, true, true))));

        var responses = service.findAll();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).available()).isFalse();
        assertThat(responses.get(0).unavailableReason()).isEqualTo("PROVIDER_DELETED");
        assertThat(responses.get(0).project().name()).isEqualTo("P2");
    }

    @Test
    void findAllMarksAgentOfDisabledRuntimeUnavailable() {
        when(agentRepository.findAllWithRelations()).thenReturn(List.of(agentWith(provider(7L, false, false))));

        var responses = service.findAll();

        assertThat(responses.get(0).available()).isFalse();
        assertThat(responses.get(0).unavailableReason()).isEqualTo("RUNTIME_DISABLED");
    }

    @Test
    void findAllMarksAgentAvailableWhenRuntimeEnabledAndWorkspaceConnected() {
        AiProvider provider = provider(7L, false, true);
        Agent agent = agentWith(provider);
        Workspace workspace = workspace(5L);
        when(agentRepository.findAllWithRelations()).thenReturn(List.of(agent));
        when(projectWorkspaceRepository.findByProjectIdIn(any())).thenReturn(List.of(link(agent.getProject(), workspace, true)));
        when(runtimeStatusRepository.findByWorkspaceIdIn(any()))
                .thenReturn(List.of(runtimeStatus(workspace, provider, ConnectionStatus.CONNECTED)));

        var responses = service.findAll();

        assertThat(responses.get(0).available()).isTrue();
        assertThat(responses.get(0).unavailableReason()).isNull();
    }

    @Test
    void findAllMarksAgentUnavailableWhenWorkspaceNeverChecked() {
        when(agentRepository.findAllWithRelations()).thenReturn(List.of(agentWith(provider(7L, false, true))));

        var responses = service.findAll();

        assertThat(responses.get(0).available()).isFalse();
        assertThat(responses.get(0).unavailableReason()).isEqualTo("CONNECTION_NOT_CONNECTED");
    }

    @Test
    void createRejectsRuntimeThatIsTurnedOff() {
        when(providerRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(provider(7L, false, false)));

        assertThatThrownBy(() -> service.create(new CreateAgentRequest(
                "A", 2L, 1L, 1L, 7L, null, "persona", null, null, null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("turned off");
        verify(agentRepository, never()).save(any());
    }

    @Test
    void assignProviderToMissingRuntimeIsNotFound() {
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agentWith(provider(7L, true, true))));
        when(providerRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignProvider(1L, 9L)).isInstanceOf(NotFoundException.class);
        verify(agentRepository, never()).save(any());
    }

    @Test
    void assignProviderRejectsRuntimeThatIsTurnedOff() {
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agentWith(provider(7L, true, true))));
        when(providerRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.of(provider(9L, false, false)));

        assertThatThrownBy(() -> service.assignProvider(1L, 9L)).isInstanceOf(ConflictException.class);
        verify(agentRepository, never()).save(any());
    }
}
