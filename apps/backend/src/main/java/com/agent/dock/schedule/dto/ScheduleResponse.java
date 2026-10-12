package com.agent.dock.schedule.dto;

import com.agent.dock.agent.dto.AgentSummary;
import com.agent.dock.group.dto.GroupSummary;
import com.agent.dock.project.dto.ProjectSummary;
import com.agent.dock.schedule.domain.MissedPolicy;
import com.agent.dock.schedule.domain.Schedule;
import java.time.Instant;

public record ScheduleResponse(
        Long id,
        ProjectSummary project,
        String name,
        String prompt,
        AgentSummary targetAgent,
        GroupSummary targetGroup,
        String cron,
        boolean enabled,
        MissedPolicy missedPolicy,
        Instant nextRunAt,
        Instant lastRunAt,
        Long lastTaskId,
        Instant createdAt,
        Instant updatedAt
) {
    public static ScheduleResponse from(Schedule s) {
        return new ScheduleResponse(
                s.getId(),
                s.getProject() == null ? null : ProjectSummary.from(s.getProject()),
                s.getName(), s.getPrompt(),
                s.getTargetAgent() == null ? null : AgentSummary.from(s.getTargetAgent()),
                s.getTargetGroup() == null ? null : GroupSummary.from(s.getTargetGroup()),
                s.getCron(), s.isEnabled(), s.getMissedPolicy(),
                s.getNextRunAt(), s.getLastRunAt(), s.getLastTaskId(),
                s.getCreatedAt(), s.getUpdatedAt());
    }
}
