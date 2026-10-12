package com.agent.dock.schedule.dto;

import com.agent.dock.schedule.domain.MissedPolicy;
import jakarta.validation.constraints.NotBlank;

/** 예약 수정(프로젝트는 바꾸지 않는다). */
public record UpdateScheduleRequest(
        @NotBlank String name,
        @NotBlank String prompt,
        Long targetAgentId,
        Long groupId,
        @NotBlank String cron,
        Boolean enabled,
        MissedPolicy missedPolicy
) {
}
