package com.agent.dock.schedule.service;

import com.agent.dock.agent.repository.AgentRepository;
import com.agent.dock.common.exception.BadRequestException;
import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.domain.Execution;
import com.agent.dock.group.repository.AgentGroupRepository;
import com.agent.dock.project.domain.Project;
import com.agent.dock.project.repository.ProjectRepository;
import com.agent.dock.schedule.domain.MissedPolicy;
import com.agent.dock.schedule.domain.Schedule;
import com.agent.dock.schedule.dto.CreateScheduleRequest;
import com.agent.dock.schedule.dto.ScheduleResponse;
import com.agent.dock.schedule.dto.ScheduleRunResponse;
import com.agent.dock.schedule.dto.UpdateScheduleRequest;
import com.agent.dock.schedule.repository.ScheduleRepository;
import com.agent.dock.task.dto.CommandRequest;
import com.agent.dock.task.service.TaskService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {
    @Mock ScheduleRepository repository;
    @Mock ProjectRepository projectRepository;
    @Mock AgentRepository agentRepository;
    @Mock AgentGroupRepository groupRepository;
    @Mock TaskService taskService;
    @Mock EventPublisher changeEvents;
    @InjectMocks ScheduleService service;

    private static Project project(long id) {
        Project p = new Project();
        p.setId(id);
        p.setName("P" + id);
        return p;
    }

    private static Schedule schedule(long id, long projectId, String cron, Instant nextRunAt) {
        Schedule s = new Schedule();
        s.setId(id);
        s.setProject(project(projectId));
        s.setProjectId(projectId);
        s.setName("S" + id);
        s.setPrompt("do it");
        s.setCron(cron);
        s.setEnabled(true);
        s.setMissedPolicy(MissedPolicy.SKIP);
        s.setNextRunAt(nextRunAt);
        return s;
    }

    private static Execution root(long id, long taskId) {
        Execution e = new Execution();
        e.setId(id);
        e.setTaskId(taskId);
        return e;
    }

    @Test
    void createStoresScheduleComputesNextRunAndNotifies() {
        when(projectRepository.findById(2L)).thenReturn(Optional.of(project(2L)));
        final Schedule[] holder = new Schedule[1];
        when(repository.save(any(Schedule.class))).thenAnswer(invocation -> {
            Schedule s = invocation.getArgument(0);
            s.setId(5L);
            holder[0] = s;
            return s;
        });
        when(repository.findWithRelations(5L)).thenAnswer(invocation -> Optional.of(holder[0]));

        ScheduleResponse res = service.create(
                new CreateScheduleRequest(2L, "nightly", "do it", null, null, "0 0 3 * * *", null, null));

        assertThat(res.id()).isEqualTo(5L);
        assertThat(res.name()).isEqualTo("nightly");
        assertThat(res.missedPolicy()).isEqualTo(MissedPolicy.SKIP);
        assertThat(res.enabled()).isTrue();
        assertThat(res.nextRunAt()).isNotNull();
        verify(changeEvents).scheduleChanged(2L);
    }

    @Test
    void createRejectsInvalidCron() {
        when(projectRepository.findById(2L)).thenReturn(Optional.of(project(2L)));

        assertThatThrownBy(() -> service.create(
                new CreateScheduleRequest(2L, "x", "y", null, null, "not a cron", null, null)))
                .isInstanceOf(BadRequestException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsAgentAndGroupTogether() {
        when(projectRepository.findById(2L)).thenReturn(Optional.of(project(2L)));

        assertThatThrownBy(() -> service.create(
                new CreateScheduleRequest(2L, "x", "y", 7L, 9L, "0 0 3 * * *", null, null)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void updateRecomputesNextRunWhenCronChanges() {
        Schedule s = schedule(7L, 2L, "0 0 3 * * *", Instant.now().plus(5, ChronoUnit.HOURS));
        when(repository.findWithRelations(7L)).thenReturn(Optional.of(s));
        when(repository.save(any(Schedule.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.update(7L,
                new UpdateScheduleRequest("new", "cmd", null, null, "0 0 4 * * *", true, MissedPolicy.RUN_ONCE));

        assertThat(s.getCron()).isEqualTo("0 0 4 * * *");
        assertThat(s.getMissedPolicy()).isEqualTo(MissedPolicy.RUN_ONCE);
        assertThat(s.getNextRunAt()).isAfter(Instant.now());
        verify(changeEvents).scheduleChanged(2L);
    }

    @Test
    void deleteRemovesScheduleAndNotifies() {
        Schedule s = schedule(7L, 2L, "0 0 * * * *", null);
        when(repository.findWithRelations(7L)).thenReturn(Optional.of(s));

        service.delete(7L);

        verify(repository).delete(s);
        verify(changeEvents).scheduleChanged(2L);
    }

    @Test
    void findOneMissingIsNotFound() {
        when(repository.findWithRelations(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOne(99L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void dueIdsDelegatesToRepository() {
        Instant now = Instant.now();
        when(repository.findDueIds(now)).thenReturn(List.of(1L, 2L));

        assertThat(service.dueIds(now)).containsExactly(1L, 2L);
    }

    @Test
    void triggerRunsUpcomingScheduleAndAdvancesNextRun() {
        Instant before = Instant.now();
        Schedule s = schedule(7L, 2L, "0 0 3 * * *", before);
        when(repository.findById(7L)).thenReturn(Optional.of(s));
        when(taskService.command(eq(2L), any(CommandRequest.class))).thenReturn(root(100L, 30L));

        service.trigger(7L);

        verify(taskService).command(eq(2L), any(CommandRequest.class));
        assertThat(s.getLastTaskId()).isEqualTo(30L);
        assertThat(s.getNextRunAt()).isAfter(before);
        verify(changeEvents).scheduleChanged(2L);
    }

    @Test
    void triggerSkipsMissedScheduleWhenPolicyIsSkip() {
        Instant before = Instant.now();
        Schedule s = schedule(7L, 2L, "0 0 3 * * *", before.minus(2, ChronoUnit.DAYS));
        when(repository.findById(7L)).thenReturn(Optional.of(s));

        service.trigger(7L);

        verify(taskService, never()).command(any(), any());
        assertThat(s.getLastTaskId()).isNull();
        assertThat(s.getNextRunAt()).isAfter(before);
        verify(repository).save(s);
    }

    @Test
    void triggerRunsOnceForMissedScheduleWhenPolicyIsRunOnce() {
        Instant before = Instant.now();
        Schedule s = schedule(7L, 2L, "0 0 3 * * *", before.minus(2, ChronoUnit.DAYS));
        s.setMissedPolicy(MissedPolicy.RUN_ONCE);
        when(repository.findById(7L)).thenReturn(Optional.of(s));
        when(taskService.command(eq(2L), any(CommandRequest.class))).thenReturn(root(100L, 30L));

        service.trigger(7L);

        verify(taskService).command(eq(2L), any(CommandRequest.class));
        assertThat(s.getLastTaskId()).isEqualTo(30L);
        assertThat(s.getLastRunAt()).isNotNull();
    }

    @Test
    void triggerIgnoresDisabledOrFutureSchedule() {
        Schedule disabled = schedule(7L, 2L, "0 0 3 * * *", Instant.now().minusSeconds(5));
        disabled.setEnabled(false);
        when(repository.findById(7L)).thenReturn(Optional.of(disabled));

        Schedule future = schedule(8L, 2L, "0 0 3 * * *", Instant.now().plus(1, ChronoUnit.HOURS));
        when(repository.findById(8L)).thenReturn(Optional.of(future));

        service.trigger(7L);
        service.trigger(8L);

        verify(repository, never()).save(any());
        verify(taskService, never()).command(any(), any());
    }

    @Test
    void runNowExecutesImmediatelyAndRecordsLastRun() {
        Schedule s = schedule(7L, 2L, "0 0 3 * * *", Instant.now().plus(1, ChronoUnit.HOURS));
        when(repository.findWithRelations(7L)).thenReturn(Optional.of(s));
        when(taskService.command(eq(2L), any(CommandRequest.class))).thenReturn(root(100L, 30L));

        ScheduleRunResponse res = service.runNow(7L);

        assertThat(res.scheduleId()).isEqualTo(7L);
        assertThat(res.taskId()).isEqualTo(30L);
        assertThat(res.executionId()).isEqualTo(100L);
        assertThat(s.getLastTaskId()).isEqualTo(30L);
        assertThat(s.getLastRunAt()).isNotNull();
        verify(repository).save(s);
        verify(changeEvents).scheduleChanged(2L);
    }
}