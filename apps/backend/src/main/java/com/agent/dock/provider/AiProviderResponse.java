package com.agent.dock.provider;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record AiProviderResponse(
        Long id, ProviderKey key, String name, Map<String, Object> capabilities,
        List<AiConnectionResponse> connections, Instant createdAt, Instant updatedAt
) {
    public static AiProviderResponse from(AiProvider p) {
        List<AiConnectionResponse> connections = p.getConnections() == null ? List.of() :
                p.getConnections().stream().map(AiConnectionResponse::from).toList();
        return new AiProviderResponse(p.getId(), p.getKey(), p.getName(), p.getCapabilities(),
                connections, p.getCreatedAt(), p.getUpdatedAt());
    }
}
