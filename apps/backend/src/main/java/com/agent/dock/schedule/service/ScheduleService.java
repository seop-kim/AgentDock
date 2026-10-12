package com.agent.dock.schedule.service;

import com.agent.dock.agent.repository.AgentRepository;
import com.agent.dock.common.exception.BadRequestException;
import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.domain.Execution;
import com.agent.dock.group.domain.AgentGroup;
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
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 반복·예약 실행(schedule)의 서비스.
 *
 * <p>예약은 "언제 명령을 보낼지"만 정하고, 실제 실행은 채팅 명령 경로({@link TaskService#command})를 그대로
 * 재사용한다 — 예약 전용 실행 경로를 만들지 않는다. 저장된 {@code nextRunAt} 을 스케줄러가 주기적으로 훑어
 * 지난 것을 실행하고 다음 시각으로 옮긴다(놓친 회차는 {@link MissedPolicy} 로 처리).
 */
@Service
@RequiredArgsConstructor
public class ScheduleService {
    private final ScheduleRepository repository;
    private final ProjectRepository projectRepository;
    private final AgentRepository agentRepository;
    private final AgentGroupRepository groupRepository;
    private final TaskService taskService;
    /** 전역 SSE 스트림에 "예약이 바뀌었다"를 알린다. */
    private final EventPublisher changeEvents;

    /** cron 의 다음 시각을 계산하는 기준 시간대(서버 로컬). */
    private static final ZoneId ZONE = ZoneId.systemDefault();

    public List<ScheduleResponse> findAll(Long projectId) {
        List<Schedule> schedules = projectId == null
                ? repository.findAllWithRelations()
                : repository.findByProjectWithRelations(projectId);
        return schedules.stream().map(ScheduleResponse::from).toList();
    }

    public ScheduleResponse findOne(Long id) {
        return ScheduleResponse.from(require(id));
    }

    @Transactional
    public ScheduleResponse create(CreateScheduleRequest request) {
        Project project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new NotFoundException("Project %d not found".formatted(request.projectId())));
        requireTargetsConsistent(request.targetAgentId(), request.groupId());
        CronExpression cron = parseCron(request.cron());

        Schedule schedule = new Schedule();
        schedule.setProject(project);
        apply(schedule, request.name(), request.prompt(), request.targetAgentId(), request.groupId(),
                request.cron(), request.enabled() == null || request.enabled(),
                request.missedPolicy() == null ? MissedPolicy.SKIP : request.missedPolicy());
        schedule.setNextRunAt(nextRun(cron, Instant.now()));
        Schedule saved = repository.save(schedule);
        changeEvents.scheduleChanged(project.getId());
        return findOne(saved.getId());
    }

    @Transactional
    public ScheduleResponse update(Long id, UpdateScheduleRequest request) {
        Schedule schedule = require(id);
        requireTargetsConsistent(request.targetAgentId(), request.groupId());
        CronExpression cron = parseCron(request.cron());
        boolean cronChanged = !schedule.getCron().equals(request.cron().strip());
        boolean turningOn = !schedule.isEnabled() && Boolean.TRUE.equals(request.enabled());
        apply(schedule, request.name(), request.prompt(), request.targetAgentId(), request.groupId(),
                request.cron(), request.enabled() == null ? schedule.isEnabled() : request.enabled(),
                request.missedPolicy() == null ? schedule.getMissedPolicy() : request.missedPolicy());
        if (cronChanged || turningOn || schedule.getNextRunAt() == null) {
            schedule.setNextRunAt(nextRun(cron, Instant.now()));
        }
        repository.save(schedule);
        changeEvents.scheduleChanged(schedule.getProjectId());
        return findOne(id);
    }

    @Transactional
    public void delete(Long id) {
        Schedule schedule = require(id);
        repository.delete(schedule);
        changeEvents.scheduleChanged(schedule.getProjectId());
    }

    /** 즉시 실행 — 채팅 명령 경로를 그대로 쓴다. 마지막 실행 기록을 남긴다. */
    @Transactional
    public ScheduleRunResponse runNow(Long id) {
        Schedule schedule = require(id);
        Execution root = triggerCommand(schedule);
        Instant now = Instant.now();
        schedule.setLastRunAt(now);
        schedule.setLastTaskId(root.getTaskId());
        repository.save(schedule);
        changeEvents.scheduleChanged(schedule.getProjectId());
        return new ScheduleRunResponse(schedule.getId(), root.getTaskId(), root.getId(), now);
    }

    /** 지금 실행할 때가 된 예약 id. 스케줄러가 이 목록을 워커에 넘긴다. */
    public List<Long> dueIds(Instant now) {
        return repository.findDueIds(now);
    }

    /**
     * 예약 하나를 실행한다(스케줄러 워커 스레드가 부른다).
     * 놓친 회차가 있고 정책이 SKIP 이면 실행하지 않고 다음 시각만 앞으로 옮긴다.
     */
    @Transactional
    public void trigger(Long id) {
        Schedule schedule = repository.findById(id).orElse(null);
        Instant now = Instant.now();
        if (schedule == null || !schedule.isEnabled() || schedule.getNextRunAt() == null
                || schedule.getNextRunAt().isAfter(now)) {
            return;
        }
        CronExpression cron = parseCron(schedule.getCron());
        boolean skipMissed = missed(cron, schedule.getNextRunAt(), now)
                && schedule.getMissedPolicy() == MissedPolicy.SKIP;
        if (!skipMissed) {
            Execution root = triggerCommand(schedule);
            schedule.setLastRunAt(now);
            schedule.setLastTaskId(root.getTaskId());
        }
        schedule.setNextRunAt(nextRun(cron, now));
        repository.save(schedule);
        changeEvents.scheduleChanged(schedule.getProjectId());
    }

    /** 예약의 명령을 채팅 명령 경로로 보낸다(대상이 없으면 프로젝트 마스터가 받는다). */
    private Execution triggerCommand(Schedule schedule) {
        return taskService.command(schedule.getProjectId(),
                new CommandRequest(schedule.getPrompt(), schedule.getTargetAgentId(), schedule.getGroupId(), null));
    }

    /** 저장된 다음 실행 시각이 지금보다 한 주기 이상 과거면 놓친 회차가 있다고 본다. */
    private boolean missed(CronExpression cron, Instant nextRunAt, Instant now) {
        Instant following = nextRun(cron, nextRunAt);
        return following != null && following.isBefore(now);
    }

    private void apply(Schedule schedule, String name, String prompt, Long targetAgentId, Long groupId,
                       String cron, boolean enabled, MissedPolicy missedPolicy) {
        schedule.setName(name);
        schedule.setPrompt(prompt);
        schedule.setCron(cron.strip());
        schedule.setEnabled(enabled);
        schedule.setMissedPolicy(missedPolicy);
        schedule.setTargetAgent(targetAgentId == null ? null : agentRepository.findById(targetAgentId)
                .orElseThrow(() -> new NotFoundException("Agent %d not found".formatted(targetAgentId))));
        if (groupId == null) {
            schedule.setTargetGroup(null);
        } else {
            AgentGroup group = groupRepository.findById(groupId)
                    .orElseThrow(() -> new NotFoundException("Group %d not found".formatted(groupId)));
            if (!group.getProject().getId().equals(schedule.getProject().getId())) {
                throw new BadRequestException("Group does not belong to project");
            }
            schedule.setTargetGroup(group);
        }
    }

    private void requireTargetsConsistent(Long targetAgentId, Long groupId) {
        if (targetAgentId != null && groupId != null) {
            throw new BadRequestException("targetAgentId 와 groupId 는 함께 쓸 수 없습니다");
        }
    }

    /** cron 식을 파싱한다. 형식이 틀리면 400. */
    private static CronExpression parseCron(String cron) {
        try {
            return CronExpression.parse(cron.strip());
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid cron expression: " + cron);
        }
    }

    /** cron 의 다음 실행 시각. 계산할 수 없으면 null. */
    private static Instant nextRun(CronExpression cron, Instant from) {
        try {
            ZonedDateTime next = cron.next(from.atZone(ZONE));
            return next == null ? null : next.toInstant();
        } catch (IllegalStateException ex) {
            return null;
        }
    }

    private Schedule require(Long id) {
        return repository.findWithRelations(id)
                .orElseThrow(() -> new NotFoundException("Schedule %d not found".formatted(id)));
    }
}