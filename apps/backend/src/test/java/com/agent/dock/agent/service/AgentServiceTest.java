package com.agent.dock.agent.service;

import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.agent.domain.Agent;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.agent.dto.CreateAgentRequest;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.agent.dto.UpdateAgentRequest;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.agent.repository.AgentRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.attachment.repository.AttachmentRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.common.exception.ConflictException;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.repository.ExecutionLogRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.repository.ExecutionRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.group.domain.AgentGroup;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.group.domain.AgentGroupMember;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.group.repository.AgentGroupMemberRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.group.repository.AgentGroupRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.permission.domain.PermissionProfile;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.permission.repository.PermissionProfileRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.project.domain.Project;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.project.domain.ProjectWorkspace;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.project.repository.ProjectRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.project.repository.ProjectWorkspaceRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.provider.domain.AiProvider;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.provider.domain.ConnectionStatus;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.provider.domain.ProviderKey;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.provider.repository.AiProviderRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.role.domain.AgentRole;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.role.repository.AgentRoleRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.task.repository.TaskRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.workspace.domain.Workspace;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.workspace.domain.WorkspaceRuntimeStatus;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.workspace.repository.WorkspaceRuntimeStatusRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {
    @Mock AgentRepository agentRepository;
    @Mock AgentRoleRepository roleRepository;
    @Mock PermissionProfileRepository permissionProfileRepository;
    @Mock AiProviderRepository providerRepository;
    @Mock ProjectRepository projectRepository;
    @Mock ProjectWorkspaceRepository projectWorkspaceRepository;
    @Mock WorkspaceRuntimeStatusRepository runtimeStatusRepository;
    @Mock AgentGroupRepository groupRepository;
    @Mock AgentGroupMemberRepository memberRepository;
    @Mock TaskRepository taskRepository;
    @Mock ExecutionRepository executionRepository;
    @Mock ExecutionLogRepository executionLogRepository;
    @Mock AttachmentRepository attachmentRepository;
    @Mock EventPublisher changeEvents;
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

    private AgentGroup group(long id, Project project, Long leaderAgentId) {
        AgentGroup group = new AgentGroup();
        group.setId(id);
        group.setProject(project);
        group.setName("G" + id);
        group.setLeaderAgentId(leaderAgentId);
        return group;
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
    void findAllIncludesPlacementState() {
        Agent agent = agentWith(provider(7L, false, true));
        agent.setPlaced(false);
        agent.setNodeX(-12.5);
        agent.setNodeY(30.0);
        when(agentRepository.findAllWithRelations()).thenReturn(List.of(agent));

        var response = service.findAll().get(0);

        assertThat(response.placed()).isFalse();
        assertThat(response.nodeX()).isEqualTo(-12.5);
        assertThat(response.nodeY()).isEqualTo(30.0);
    }

    @Test
    void createRejectsRuntimeThatIsTurnedOff() {
        when(providerRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(provider(7L, false, false)));

        assertThatThrownBy(() -> service.create(new CreateAgentRequest(
                "A", 2L, 1L, 1L, 7L, "persona", null, null, null)))
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

    @Test
    void updateRejectsRuntimeThatIsTurnedOff() {
        when(agentRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(agentWith(provider(7L, false, true))));
        when(providerRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.of(provider(9L, false, false)));

        assertThatThrownBy(() -> service.update(1L, new UpdateAgentRequest("A", 1L, 1L, 9L, "m", "d", "p")))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("turned off");
        verify(agentRepository, never()).save(any());
    }

    @Test
    void updateRejectsDeletedRuntime() {
        when(agentRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(agentWith(provider(7L, false, true))));
        when(providerRepository.findByIdAndDeletedAtIsNull(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, new UpdateAgentRequest("A", 1L, 1L, 9L, "m", "d", "p")))
                .isInstanceOf(NotFoundException.class);
        verify(agentRepository, never()).save(any());
    }

    @Test
    void updateOfMissingAgentIsNotFound() {
        when(agentRepository.findByIdWithRelations(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, new UpdateAgentRequest("A", 1L, 1L, 9L, "m", "d", "p")))
                .isInstanceOf(NotFoundException.class);
        verify(agentRepository, never()).save(any());
    }

    @Test
    void updateAppliesEditableFields() {
        AiProvider enabled = provider(7L, false, true);
        Agent agent = agentWith(enabled);
        AgentRole role = new AgentRole();
        role.setId(3L);
        role.setName("Dev");
        PermissionProfile profile = new PermissionProfile();
        profile.setId(4L);
        profile.setName("Full");
        when(agentRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(agent));
        when(providerRepository.findByIdAndDeletedAtIsNull(7L)).thenReturn(Optional.of(enabled));
        when(roleRepository.findById(3L)).thenReturn(Optional.of(role));
        when(permissionProfileRepository.findById(4L)).thenReturn(Optional.of(profile));
        when(agentRepository.save(any(Agent.class))).thenAnswer(inv -> inv.getArgument(0));
        when(projectWorkspaceRepository.findByProjectIdIn(any())).thenReturn(List.of());

        var response = service.update(1L, new UpdateAgentRequest("Renamed", 3L, 4L, 7L, "sonnet", "plan", "be nice"));

        assertThat(response.name()).isEqualTo("Renamed");
        assertThat(response.role().id()).isEqualTo(3L);
        assertThat(response.permissionProfile().id()).isEqualTo(4L);
        assertThat(agent.getModel()).isEqualTo("sonnet");
        assertThat(agent.getMode()).isEqualTo("plan");
        assertThat(agent.getPersona()).isEqualTo("be nice");
        verify(agentRepository).save(agent);
    }

    @Test
    void deleteRefusesMasterAgent() {
        Agent agent = agentWith(provider(7L, false, true));
        agent.getProject().setMasterAgentId(1L);
        when(agentRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(agent));

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("마스터");
        verify(agentRepository, never()).delete(any());
    }

    @Test
    void deleteRemovesAgentFromGroupsAndPromotesNextMember() {
        Agent leader = agentWith(provider(7L, false, true));
        Agent next = new Agent();
        next.setId(2L);
        next.setName("Dev B");
        next.setProject(leader.getProject());
        AgentGroup group = group(10L, leader.getProject(), 1L);
        when(agentRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(leader));
        when(groupRepository.findByProjectWithRelations(2L)).thenReturn(List.of(group));
        AgentGroupMember membership = new AgentGroupMember();
        when(memberRepository.findMember(10L, 1L)).thenReturn(Optional.of(membership));
        AgentGroupMember remaining = new AgentGroupMember();
        remaining.setAgent(next);
        when(memberRepository.findMembersWithAgent(10L)).thenReturn(List.of(remaining));
        when(taskRepository.findIdsByAgentId(1L)).thenReturn(List.of());
        when(executionRepository.findIdsByAgentId(1L)).thenReturn(List.of());

        service.delete(1L);

        verify(memberRepository).delete(membership);
        verify(groupRepository).save(group);
        assertThat(group.getLeaderAgent()).isEqualTo(next);
        verify(executionRepository).clearDelegatedTarget(1L);
        verify(agentRepository).delete(leader);
    }

    @Test
    void deleteLeavesLeaderNullWhenGroupHasNoOtherMember() {
        Agent leader = agentWith(provider(7L, false, true));
        AgentGroup group = group(10L, leader.getProject(), 1L);
        when(agentRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(leader));
        when(groupRepository.findByProjectWithRelations(2L)).thenReturn(List.of(group));
        when(memberRepository.findMember(10L, 1L)).thenReturn(Optional.empty());
        when(memberRepository.findMembersWithAgent(10L)).thenReturn(List.of());
        when(taskRepository.findIdsByAgentId(1L)).thenReturn(List.of());
        when(executionRepository.findIdsByAgentId(1L)).thenReturn(List.of());

        service.delete(1L);

        verify(groupRepository).save(group);
        assertThat(group.getLeaderAgent()).isNull();
        verify(agentRepository).delete(leader);
    }

    @Test
    void deleteRemovesAssignedTasksWithExecutionLogsFirst() {
        Agent agent = agentWith(provider(7L, false, true));
        when(agentRepository.findByIdWithRelations(1L)).thenReturn(Optional.of(agent));
        when(groupRepository.findByProjectWithRelations(2L)).thenReturn(List.of());
        when(taskRepository.findIdsByAgentId(1L)).thenReturn(List.of(30L));
        when(executionRepository.findIdsByTaskIdIn(List.of(30L))).thenReturn(List.of(100L));
        when(executionRepository.findIdsByAgentId(1L)).thenReturn(List.of(100L, 101L));

        service.delete(1L);

        InOrder inOrder = inOrder(executionLogRepository, executionRepository, attachmentRepository, taskRepository);
        inOrder.verify(executionLogRepository).deleteByExecutionIdIn(List.of(100L));
        inOrder.verify(executionRepository).deleteAllByIdInBatch(List.of(100L));
        inOrder.verify(attachmentRepository).deleteByTaskIdIn(List.of(30L));
        inOrder.verify(taskRepository).deleteAllByIdInBatch(List.of(30L));
        inOrder.verify(executionLogRepository).deleteByExecutionIdIn(List.of(100L, 101L));
        inOrder.verify(executionRepository).deleteAllByIdInBatch(List.of(100L, 101L));
        verify(executionRepository).clearDelegatedTarget(1L);
        verify(agentRepository).delete(agent);
    }
}
