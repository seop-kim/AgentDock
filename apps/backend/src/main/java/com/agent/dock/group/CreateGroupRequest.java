package com.agent.dock.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateGroupRequest(
        @NotNull Long projectId,
        @NotBlank String name,
        String description,
        Long leaderAgentId
) {}
