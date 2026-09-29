package com.agent.dock.provider;

import jakarta.validation.constraints.NotNull;

public record CreateAiConnectionRequest(
        @NotNull Long providerId,
        String accountName,
        String credentialReference
) {}
