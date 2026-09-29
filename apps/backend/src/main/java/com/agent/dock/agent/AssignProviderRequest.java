package com.agent.dock.agent;

import jakarta.validation.constraints.NotNull;

public record AssignProviderRequest(@NotNull Long providerId) {
}
