package com.agent.dock.group.service;

import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.agent.repository.AgentRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.attachment.repository.AttachmentRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.repository.ExecutionLogRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.repository.ExecutionRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.group.domain.AgentGroup;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.group.repository.AgentGroupMemberRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.group.repository.AgentGroupRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.project.repository.ProjectRepository;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.task.repository.TaskRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {
    @Mock AgentGroupRepository groupRepository;
    @Mock AgentGroupMemberRepository memberRepository;
    @Mock ProjectRepository projectRepository;
    @Mock AgentRepository agentRepository;
    @Mock TaskRepository taskRepository;
    @Mock ExecutionRepository executionRepository;
    @Mock ExecutionLogRepository executionLogRepository;
    @Mock AttachmentRepository attachmentRepository;
    @Mock EventPublisher changeEvents;
    @InjectMocks GroupService service;

    private AgentGroup group(long id) {
        AgentGroup group = new AgentGroup();
        group.setId(id);
        group.setName("G" + id);
        return group;
    }

    @Test
    void deleteRemovesMembersBeforeGroup() {
        AgentGroup group = group(5L);
        when(groupRepository.findWithRelations(5L)).thenReturn(Optional.of(group));
        when(taskRepository.findIdsByGroupId(5L)).thenReturn(List.of());

        service.delete(5L);

        InOrder inOrder = inOrder(memberRepository, groupRepository);
        inOrder.verify(memberRepository).deleteByGroupIdIn(List.of(5L));
        inOrder.verify(groupRepository).delete(group);
    }

    @Test
    void deleteRemovesGroupTasksWithExecutionsAndAttachments() {
        AgentGroup group = group(5L);
        when(groupRepository.findWithRelations(5L)).thenReturn(Optional.of(group));
        when(taskRepository.findIdsByGroupId(5L)).thenReturn(List.of(30L));
        when(executionRepository.findIdsByTaskIdIn(List.of(30L))).thenReturn(List.of(100L));

        service.delete(5L);

        InOrder inOrder = inOrder(executionLogRepository, executionRepository, attachmentRepository, taskRepository);
        inOrder.verify(executionLogRepository).deleteByExecutionIdIn(List.of(100L));
        inOrder.verify(executionRepository).deleteAllByIdInBatch(List.of(100L));
        inOrder.verify(attachmentRepository).deleteByTaskIdIn(List.of(30L));
        inOrder.verify(taskRepository).deleteAllByIdInBatch(List.of(30L));
        verify(memberRepository).deleteByGroupIdIn(List.of(5L));
        verify(groupRepository).delete(group);
    }

    @Test
    void deleteOfMissingGroupIsNotFound() {
        when(groupRepository.findWithRelations(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(5L)).isInstanceOf(NotFoundException.class);
        verify(groupRepository, never()).delete(any());
        verify(memberRepository, never()).deleteByGroupIdIn(any());
    }
}
