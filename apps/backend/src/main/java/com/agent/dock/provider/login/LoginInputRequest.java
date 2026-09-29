package com.agent.dock.provider.login;

import jakarta.validation.constraints.NotNull;

public record LoginInputRequest(@NotNull String text) {
}
