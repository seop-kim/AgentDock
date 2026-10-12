package com.agent.dock.schedule.dto;

import com.agent.dock.schedule.domain.MissedPolicy;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 예약 생성. 대상(targetAgentId/groupId)을 주지 않으면 프로젝트 마스터가 받는다. */
public record CreateScheduleRequest(
        @NotNull Long projectId,
        @NotBlank String name,
        @NotBlank String prompt,
        Long targetAgentId,
        Long groupId,
        @NotBlank String cron,
        Boolean enabled,
        MissedPolicy missedPolicy
) {
}
