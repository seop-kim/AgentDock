package com.agent.dock.agent.dto;

import com.agent.dock.agent.domain.Agent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record CreateAgentRequest(
        @NotBlank String name,
        @NotNull Long projectId,
        @NotNull Long roleId,
        @NotNull Long permissionProfileId,
        @NotNull Long providerId,
        String persona,
        String model,
        String mode,
        Map<String, Object> profile
) {
}
