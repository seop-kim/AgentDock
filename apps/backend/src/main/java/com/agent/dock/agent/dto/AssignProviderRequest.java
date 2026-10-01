package com.agent.dock.agent.dto;

import com.agent.dock.agent.domain.Agent;
import jakarta.validation.constraints.NotNull;

public record AssignProviderRequest(@NotNull Long providerId) {
}
