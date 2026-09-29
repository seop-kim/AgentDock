package com.agent.dock.provider;

import java.time.Instant;
import java.util.Map;

public record AiProviderSummary(Long id, ProviderKey key, String name, Map<String, Object> capabilities, Instant createdAt, Instant updatedAt) {
    public static AiProviderSummary from(AiProvider p) {
        return new AiProviderSummary(p.getId(), p.getKey(), p.getName(), p.getCapabilities(), p.getCreatedAt(), p.getUpdatedAt());
    }
}
