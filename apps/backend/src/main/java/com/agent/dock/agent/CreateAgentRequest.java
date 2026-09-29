package com.agent.dock.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CreateAgentRequest(
        @NotBlank String name,
        @NotNull Long projectId,
        @NotNull Long roleId,
        @NotNull Long permissionProfileId,
        @NotNull Long providerId,
        Long connectionId,
        String persona,
        String model,
        String mode,
        Map<String, Object> profile
) {
}
