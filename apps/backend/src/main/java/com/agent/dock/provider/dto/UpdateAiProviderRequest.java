package com.agent.dock.provider.dto;

import jakarta.validation.constraints.NotBlank;

/** 런타임 표시 이름 변경(PUT /ai-providers/{id}). */
public record UpdateAiProviderRequest(@NotBlank String name) {
}
