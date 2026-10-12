package com.agent.dock.execution.service;

import com.agent.dock.common.exception.BadRequestException;
import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.execution.domain.ExecutionStatus;
import com.agent.dock.execution.dto.ExecutionStatsResponse;
import com.agent.dock.execution.repository.ExecutionRepository;
import com.agent.dock.execution.repository.ExecutionUsage;
import com.agent.dock.group.domain.AgentGroup;
import com.agent.dock.group.repository.AgentGroupRepository;
import com.agent.dock.project.domain.Project;
import com.agent.dock.project.repository.ProjectRepository;
import com.agent.dock.task.domain.Task;
import com.agent.dock.task.repository.TaskRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 실행 집계(대시보드)와 로그 다중 스트림 id 파싱. 집계는 프로젝트별(task.project_id)·팀별(task.group_id)로
 * 실행 수·비용·입출력 토큰을 더하고, 값은 CLI 계측값만 쓴다(추정 금지).
 */
@ExtendWith(MockitoExtension.class)
class ExecutionStatsServiceTest {

    @Mock ExecutionRepository executionRepository;
    @Mock TaskRepository taskRepository;
    @Mock ProjectRepository projectRepository;
    @Mock AgentGroupRepository groupRepository;
    @Mock ExecutionStreamHub streamHub;
    @InjectMocks ExecutionService service;
    @Test
    void statsSumCostTokensRunningCountAndGroupByProjectAndTeam() {
        ExecutionUsage first = new ExecutionUsage(10L, ExecutionStatus.SUCCEEDED, new BigDecimal("0.10"), 100, 40);
        ExecutionUsage second = new ExecutionUsage(10L, ExecutionStatus.RUNNING, new BigDecimal("0.05"), 50, 20);
        ExecutionUsage third = new ExecutionUsage(11L, ExecutionStatus.SUCCEEDED, new BigDecimal("0.20"), 10, 5);
        when(executionRepository.findUsageGlobal()).thenReturn(List.of(first, second, third));
        when(taskRepository.findAllById(any())).thenReturn(List.of(task(10L, 6L, 9L), task(11L, 6L, null)));
        Project project = new Project();
        project.setId(6L);
        project.setName("P6");
        when(projectRepository.findAllById(any())).thenReturn(List.of(project));
        when(groupRepository.findAllById(any())).thenReturn(List.of(group(9L, 6L, "Backend")));

        ExecutionStatsResponse stats = service.stats(null);

        assertThat(stats.executionCount()).isEqualTo(3);
        assertThat(stats.runningCount()).isEqualTo(1);
        assertThat(stats.totalCostUsd()).isEqualByComparingTo("0.35");
        assertThat(stats.totalInputTokens()).isEqualTo(160);
        assertThat(stats.totalOutputTokens()).isEqualTo(65);
        assertThat(stats.byProject()).hasSize(1);
        ExecutionStatsResponse.ProjectUsage projectUsage = stats.byProject().get(0);
        assertThat(projectUsage.projectId()).isEqualTo(6L);
        assertThat(projectUsage.projectName()).isEqualTo("P6");
        assertThat(projectUsage.executionCount()).isEqualTo(3);
        assertThat(projectUsage.costUsd()).isEqualByComparingTo("0.35");
        assertThat(stats.byGroup()).hasSize(1);
        ExecutionStatsResponse.GroupUsage groupUsage = stats.byGroup().get(0);
        assertThat(groupUsage.groupId()).isEqualTo(9L);
        assertThat(groupUsage.groupName()).isEqualTo("Backend");
        assertThat(groupUsage.executionCount()).isEqualTo(2);
        assertThat(groupUsage.costUsd()).isEqualByComparingTo("0.15");
        assertThat(groupUsage.inputTokens()).isEqualTo(150);
    }

    private static Task task(Long id, Long projectId, Long groupId) {
        Task task = new Task();
        task.setId(id);
        task.setProjectId(projectId);
        task.setGroupId(groupId);
        return task;
    }

    private static AgentGroup group(Long id, Long projectId, String name) {
        AgentGroup group = new AgentGroup();
        group.setId(id);
        group.setProjectId(projectId);
        group.setName(name);
        return group;
    }
    @Test
    void statsForAProjectNarrowsToItsTasks() {
        when(projectRepository.existsById(6L)).thenReturn(true);
        when(taskRepository.findIdsByProjectId(6L)).thenReturn(List.of(10L));
        when(executionRepository.findUsageByTaskIds(List.of(10L))).thenReturn(
                List.of(new ExecutionUsage(10L, ExecutionStatus.SUCCEEDED, new BigDecimal("0.10"), 100, 40)));
        when(taskRepository.findAllById(any())).thenReturn(List.of(task(10L, 6L, null)));
        when(projectRepository.findAllById(any())).thenReturn(List.of());
        when(groupRepository.findAllById(any())).thenReturn(List.of());

        ExecutionStatsResponse stats = service.stats(6L);

        assertThat(stats.executionCount()).isEqualTo(1);
        assertThat(stats.byGroup()).isEmpty();
    }

    @Test
    void statsForUnknownProjectIsNotFound() {
        when(projectRepository.existsById(99999L)).thenReturn(false);

        assertThatThrownBy(() -> service.stats(99999L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void statsForAProjectWithNoTasksIsEmpty() {
        when(projectRepository.existsById(6L)).thenReturn(true);
        when(taskRepository.findIdsByProjectId(6L)).thenReturn(List.of());

        ExecutionStatsResponse stats = service.stats(6L);

        assertThat(stats.executionCount()).isZero();
        assertThat(stats.byProject()).isEmpty();
    }
    @Test
    void streamManyParsesCommaSeparatedIds() {
        when(streamHub.subscribeMany(List.of(1L, 2L))).thenReturn(new SseEmitter(0L));

        service.streamMany("1,2");

        verify(streamHub).subscribeMany(List.of(1L, 2L));
    }

    @Test
    void streamManyRejectsEmptyUnknownAndTooManyIds() {
        assertThatThrownBy(() -> service.streamMany("")).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.streamMany("1,x")).isInstanceOf(BadRequestException.class);
        String tooMany = IntStream.rangeClosed(1, 21).mapToObj(String::valueOf)
                .collect(Collectors.joining(","));
        assertThatThrownBy(() -> service.streamMany(tooMany)).isInstanceOf(BadRequestException.class);
    }
}
