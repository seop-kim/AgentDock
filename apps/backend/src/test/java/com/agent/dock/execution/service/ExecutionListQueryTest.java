package com.agent.dock.execution.service;

import com.agent.dock.agent.domain.Agent;
import com.agent.dock.agent.repository.AgentRepository;
import com.agent.dock.common.exception.BadRequestException;
import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.domain.ExecutionStatus;
import com.agent.dock.execution.dto.ExecutionSummaryResponse;
import com.agent.dock.execution.repository.ExecutionRepository;
import com.agent.dock.group.domain.AgentGroup;
import com.agent.dock.group.repository.AgentGroupRepository;
import com.agent.dock.project.repository.ProjectRepository;
import com.agent.dock.task.domain.Task;
import com.agent.dock.task.repository.TaskRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
@ExtendWith(MockitoExtension.class)
class ExecutionListQueryTest {
    @Mock ExecutionRepository executionRepository;
    @Mock ProjectRepository projectRepository;
    @Mock TaskRepository taskRepository;
    @Mock AgentRepository agentRepository;
    @Mock AgentGroupRepository groupRepository;
    @InjectMocks ExecutionService service;

    @Test
    void listSummariesResolvesProjectTaskAgentAndGroupLabels() {
        Execution execution = new Execution();
        execution.setId(104L);
        execution.setTaskId(39L);
        execution.setAgentId(13L);
        execution.setStatus(ExecutionStatus.SUCCEEDED);

        Task task = new Task();
        task.setId(39L);
        task.setProjectId(6L);
        task.setGroupId(9L);
        task.setTitle("연결 확인");

        Agent agent = new Agent();
        agent.setId(13L);
        agent.setName("총괄");

        AgentGroup group = new AgentGroup();
        group.setId(9L);
        group.setName("Backend Team");

        when(executionRepository.searchSummaries(eq(6L), any(), eq(100), isNull())).thenReturn(List.of(execution));
        when(taskRepository.findAllById(any())).thenReturn(List.of(task));
        when(agentRepository.findAllById(any())).thenReturn(List.of(agent));
        when(groupRepository.findAllById(any())).thenReturn(List.of(group));
        when(projectRepository.existsById(6L)).thenReturn(true);

        List<ExecutionSummaryResponse> summaries = service.listSummaries(6L, null, null, null);

        assertThat(summaries).hasSize(1);
        ExecutionSummaryResponse summary = summaries.get(0);
        assertThat(summary.id()).isEqualTo(104L);
        assertThat(summary.projectId()).isEqualTo(6L);
        assertThat(summary.taskTitle()).isEqualTo("연결 확인");
        assertThat(summary.agentName()).isEqualTo("총괄");
        assertThat(summary.groupId()).isEqualTo(9L);
        assertThat(summary.groupName()).isEqualTo("Backend Team");
        assertThat(summary.status()).isEqualTo(ExecutionStatus.SUCCEEDED);
    }
    @Test
    void listSummariesParsesCommaSeparatedStatusesAndCursor() {
        when(projectRepository.existsById(6L)).thenReturn(true);
        when(executionRepository.searchSummaries(eq(6L),
                eq(List.of(ExecutionStatus.RUNNING, ExecutionStatus.WAITING_CHILD)), eq(2), eq(120L)))
                .thenReturn(List.of());

        List<ExecutionSummaryResponse> summaries = service.listSummaries(6L, List.of("RUNNING,WAITING_CHILD"), 2, 120L);

        assertThat(summaries).isEmpty();
        verify(executionRepository).searchSummaries(eq(6L),
                eq(List.of(ExecutionStatus.RUNNING, ExecutionStatus.WAITING_CHILD)), eq(2), eq(120L));
    }

    @Test
    void listSummariesRejectsUnknownStatus() {
        assertThatThrownBy(() -> service.listSummaries(null, List.of("BOGUS"), null, null))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("BOGUS");
        verify(executionRepository, never()).searchSummaries(any(), any(), anyInt(), any());
    }

    @Test
    void listSummariesRejectsLimitOutOfRange() {
        assertThatThrownBy(() -> service.listSummaries(null, null, 0, null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.listSummaries(null, null, 501, null))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void listSummariesRejectsUnknownProject() {
        when(projectRepository.existsById(99999L)).thenReturn(false);

        assertThatThrownBy(() -> service.listSummaries(99999L, null, null, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("99999");
    }
}