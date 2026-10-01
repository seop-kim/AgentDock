package com.agent.dock.project.service;

import com.agent.dock.agent.domain.Agent;
import com.agent.dock.agent.repository.AgentRepository;
import com.agent.dock.attachment.repository.AttachmentRepository;
import com.agent.dock.common.exception.BadRequestException;
import com.agent.dock.common.exception.ConflictException;
import com.agent.dock.execution.repository.ExecutionLogRepository;
import com.agent.dock.execution.repository.ExecutionRepository;
import com.agent.dock.group.domain.AgentGroup;
import com.agent.dock.group.repository.AgentGroupMemberRepository;
import com.agent.dock.group.repository.AgentGroupRepository;
import com.agent.dock.project.domain.Project;
import com.agent.dock.project.dto.ProjectLayoutRequest;
import com.agent.dock.project.dto.ProjectLayoutRequest.AgentPlacement;
import com.agent.dock.project.dto.ProjectLayoutRequest.GroupPlacement;
import com.agent.dock.project.dto.UpdateProjectRequest;
import com.agent.dock.project.repository.ProjectRepository;
import com.agent.dock.project.repository.ProjectWorkspaceRepository;
import com.agent.dock.task.repository.TaskRepository;
import com.agent.dock.workspace.repository.WorkspaceRepository;
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
class ProjectServiceTest {
    @Mock ProjectRepository repository;
    @Mock WorkspaceRepository workspaceRepository;
    @Mock ProjectWorkspaceRepository workspaceLinkRepository;
    @Mock AgentRepository agentRepository;
    @Mock AgentGroupRepository groupRepository;
    @Mock AgentGroupMemberRepository memberRepository;
    @Mock TaskRepository taskRepository;
    @Mock ExecutionRepository executionRepository;
    @Mock ExecutionLogRepository executionLogRepository;
    @Mock AttachmentRepository attachmentRepository;
    @InjectMocks ProjectService service;

    private Project project(long id, String name) {
        Project project = new Project();
        project.setId(id);
        project.setName(name);
        return project;
    }

    private Agent agent(long id, Project project) {
        Agent agent = new Agent();
        agent.setId(id);
        agent.setName("A" + id);
        agent.setProject(project);
        return agent;
    }

    private AgentGroup group(long id, Project project) {
        AgentGroup group = new AgentGroup();
        group.setId(id);
        group.setName("G" + id);
        group.setProject(project);
        return group;
    }

    @Test
    void updateRejectsDuplicateName() {
        when(repository.findById(1L)).thenReturn(Optional.of(project(1L, "P1")));
        when(repository.existsByName("P2")).thenReturn(true);

        assertThatThrownBy(() -> service.update(1L, new UpdateProjectRequest("P2", null)))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void updateSkipsDuplicateCheckWhenNameIsUnchanged() {
        Project project = project(1L, "P1");
        when(repository.findById(1L)).thenReturn(Optional.of(project));
        when(repository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workspaceLinkRepository.findByProjectIdOrderByIdAsc(1L)).thenReturn(List.of());

        var response = service.update(1L, new UpdateProjectRequest("P1", null));

        assertThat(response.name()).isEqualTo("P1");
        verify(repository, never()).existsByName(any());
    }

    @Test
    void updateKeepsDescriptionWhenOmitted() {
        Project project = project(1L, "P1");
        project.setDescription("old description");
        when(repository.findById(1L)).thenReturn(Optional.of(project));
        when(repository.existsByName("P2")).thenReturn(false);
        when(repository.save(any(Project.class))).thenAnswer(inv -> inv.getArgument(0));
        when(workspaceLinkRepository.findByProjectIdOrderByIdAsc(1L)).thenReturn(List.of());

        var response = service.update(1L, new UpdateProjectRequest("P2", null));

        assertThat(response.name()).isEqualTo("P2");
        assertThat(response.description()).isEqualTo("old description");
    }

    @Test
    void saveLayoutRejectsUnknownAgent() {
        when(repository.findById(1L)).thenReturn(Optional.of(project(1L, "P1")));
        when(agentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveLayout(1L, new ProjectLayoutRequest(
                List.of(new AgentPlacement(99L, 1.0, 2.0, true)), List.of())))
                .isInstanceOf(BadRequestException.class);
        verify(agentRepository, never()).save(any());
    }

    @Test
    void saveLayoutRejectsAgentOfAnotherProject() {
        Project other = project(2L, "P2");
        when(repository.findById(1L)).thenReturn(Optional.of(project(1L, "P1")));
        when(agentRepository.findById(9L)).thenReturn(Optional.of(agent(9L, other)));

        assertThatThrownBy(() -> service.saveLayout(1L, new ProjectLayoutRequest(
                List.of(new AgentPlacement(9L, 1.0, 2.0, true)), List.of())))
                .isInstanceOf(BadRequestException.class);
        verify(agentRepository, never()).save(any());
    }

    @Test
    void saveLayoutRejectsUnknownGroup() {
        when(repository.findById(1L)).thenReturn(Optional.of(project(1L, "P1")));
        when(groupRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveLayout(1L, new ProjectLayoutRequest(
                List.of(), List.of(new GroupPlacement(99L, 1.0, 2.0)))))
                .isInstanceOf(BadRequestException.class);
        verify(groupRepository, never()).save(any());
    }

    @Test
    void saveLayoutStoresPlacementAndCoordinates() {
        Project project = project(1L, "P1");
        when(repository.findById(1L)).thenReturn(Optional.of(project));
        Agent agent = agent(10L, project);
        agent.setPlaced(true);
        when(agentRepository.findById(10L)).thenReturn(Optional.of(agent));
        AgentGroup group = group(20L, project);
        when(groupRepository.findById(20L)).thenReturn(Optional.of(group));

        service.saveLayout(1L, new ProjectLayoutRequest(
                List.of(new AgentPlacement(10L, -5.0, 7.5, false)),
                List.of(new GroupPlacement(20L, 3.0, 4.0))));

        assertThat(agent.getNodeX()).isEqualTo(-5.0);
        assertThat(agent.getNodeY()).isEqualTo(7.5);
        assertThat(agent.isPlaced()).isFalse();
        assertThat(group.getNodeX()).isEqualTo(3.0);
        assertThat(group.getNodeY()).isEqualTo(4.0);
        verify(agentRepository).save(agent);
        verify(groupRepository).save(group);
    }

    @Test
    void saveLayoutKeepsPlacedWhenFlagIsOmitted() {
        Project project = project(1L, "P1");
        when(repository.findById(1L)).thenReturn(Optional.of(project));
        Agent agent = agent(10L, project);
        agent.setPlaced(false);
        when(agentRepository.findById(10L)).thenReturn(Optional.of(agent));

        service.saveLayout(1L, new ProjectLayoutRequest(
                List.of(new AgentPlacement(10L, 1.0, 2.0, null)), List.of()));

        assertThat(agent.isPlaced()).isFalse();
        assertThat(agent.getNodeX()).isEqualTo(1.0);
    }

    @Test
    void clearLayoutClearsCoordinatesAndPlacesAgents() {
        when(repository.findById(1L)).thenReturn(Optional.of(project(1L, "P1")));
        Agent agent = agent(10L, project(1L, "P1"));
        agent.setPlaced(false);
        agent.setNodeX(5.0);
        agent.setNodeY(6.0);
        when(agentRepository.findByProjectId(1L)).thenReturn(List.of(agent));
        AgentGroup group = group(20L, project(1L, "P1"));
        group.setNodeX(1.0);
        group.setNodeY(2.0);
        when(groupRepository.findByProjectId(1L)).thenReturn(List.of(group));

        service.clearLayout(1L);

        assertThat(agent.getNodeX()).isNull();
        assertThat(agent.getNodeY()).isNull();
        assertThat(agent.isPlaced()).isTrue();
        assertThat(group.getNodeX()).isNull();
        assertThat(group.getNodeY()).isNull();
        verify(agentRepository).save(agent);
        verify(groupRepository).save(group);
    }

    @Test
    void deleteClearsMasterAndRemovesInFkSafeOrder() {
        Project project = project(1L, "P1");
        project.setMasterAgentId(5L);
        when(repository.findById(1L)).thenReturn(Optional.of(project));
        when(repository.saveAndFlush(project)).thenAnswer(inv -> inv.getArgument(0));
        when(agentRepository.findByProjectId(1L)).thenReturn(List.of(agent(5L, project)));
        when(taskRepository.findIdsByProjectId(1L)).thenReturn(List.of(30L));
        when(groupRepository.findByProjectId(1L)).thenReturn(List.of(group(20L, project)));
        when(executionRepository.findIdsByTaskIdIn(List.of(30L))).thenReturn(List.of(100L));
        when(executionRepository.findIdsByAgentIdIn(List.of(5L))).thenReturn(List.of(101L));

        service.delete(1L);

        assertThat(project.getMasterAgent()).isNull();
        verify(repository).saveAndFlush(project);
        InOrder inOrder = inOrder(executionLogRepository, executionRepository, attachmentRepository,
                taskRepository, memberRepository, groupRepository, agentRepository);
        inOrder.verify(executionLogRepository).deleteByExecutionIdIn(List.of(100L, 101L));
        inOrder.verify(executionRepository).deleteAllByIdInBatch(List.of(100L, 101L));
        inOrder.verify(attachmentRepository).deleteByTaskIdIn(List.of(30L));
        inOrder.verify(taskRepository).deleteAllByIdInBatch(List.of(30L));
        inOrder.verify(memberRepository).deleteByGroupIdIn(List.of(20L));
        inOrder.verify(groupRepository).deleteAllByIdInBatch(List.of(20L));
        inOrder.verify(agentRepository).deleteAllByIdInBatch(List.of(5L));
        verify(workspaceLinkRepository).deleteByProjectId(1L);
        verify(repository).delete(project);
    }
}
