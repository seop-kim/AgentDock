package com.agent.dock.provider.dto;

import jakarta.validation.constraints.NotNull;

/** 런타임 on/off 토글 요청. */
public record RuntimeEnabledRequest(@NotNull Boolean enabled) {
}
