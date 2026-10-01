package com.agent.dock.group;

import jakarta.validation.constraints.NotBlank;

/** 전체 교체(PUT) 의미: leaderAgentId 가 null 이면 리더를 해제한다. */
public record UpdateGroupRequest(
        @NotBlank String name,
        String description,
        String prompt,
        Long leaderAgentId
) {}
