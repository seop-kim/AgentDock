package com.agent.dock.provider;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CreateAiProviderRequest(
        @NotNull ProviderKey key,
        @NotBlank String name,
        Map<String, Object> capabilities
) {}
