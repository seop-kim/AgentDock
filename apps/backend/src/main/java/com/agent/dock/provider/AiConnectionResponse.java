package com.agent.dock.provider;

import java.time.Instant;

public record AiConnectionResponse(
        Long id, Long providerId, String accountName, String credentialReference,
        ConnectionStatus status, Instant createdAt, Instant updatedAt
) {
    public static AiConnectionResponse from(AiConnection c) {
        // 저장 직후에는 insertable=false 인 shadow FK 컬럼이 아직 채워지지 않으므로 관계에서 보완한다.
        Long providerId = c.getProviderId() != null ? c.getProviderId() : (c.getProvider() != null ? c.getProvider().getId() : null);
        return new AiConnectionResponse(c.getId(), providerId, c.getAccountName(),
                c.getCredentialReference(), c.getStatus(), c.getCreatedAt(), c.getUpdatedAt());
    }
}
