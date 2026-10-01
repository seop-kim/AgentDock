package com.agent.dock.provider.dto;

import com.agent.dock.provider.domain.ProviderKey;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Map;

public record CreateAiProviderRequest(
        @NotNull ProviderKey key,
        @NotBlank String name,
        Map<String, Object> capabilities
) {}
