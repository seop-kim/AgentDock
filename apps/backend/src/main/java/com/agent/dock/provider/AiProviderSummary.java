package com.agent.dock.provider;

import java.time.Instant;
import java.util.Map;

public record AiProviderSummary(Long id, ProviderKey key, String name, Map<String, Object> capabilities, boolean enabled,
                                Instant createdAt, Instant updatedAt) {
    public static AiProviderSummary from(AiProvider p) {
        return new AiProviderSummary(p.getId(), p.getKey(), p.getName(), p.getCapabilities(), p.isEnabled(),
                p.getCreatedAt(), p.getUpdatedAt());
    }
}
