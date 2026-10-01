package com.agent.dock.provider.login.dto;

import jakarta.validation.constraints.NotNull;

public record LoginInputRequest(@NotNull String text) {
}
