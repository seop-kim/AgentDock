package com.agent.dock.provider;

import java.time.Instant;

public record AiConnectionSummary(Long id, String accountName, ConnectionStatus status, Instant createdAt, Instant updatedAt) {
    public static AiConnectionSummary from(AiConnection c) {
        return new AiConnectionSummary(c.getId(), c.getAccountName(), c.getStatus(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
