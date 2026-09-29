package com.agent.dock.provider;

import java.time.Instant;

public record AiConnectionResponse(
        Long id, Long providerId, String accountName, String credentialReference,
        ConnectionStatus status, Instant createdAt, Instant updatedAt
) {
    public static AiConnectionResponse from(AiConnection c) {
        return new AiConnectionResponse(c.getId(), c.getProviderId(), c.getAccountName(),
                c.getCredentialReference(), c.getStatus(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
