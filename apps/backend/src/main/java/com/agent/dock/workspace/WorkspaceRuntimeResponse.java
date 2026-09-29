package com.agent.dock.workspace;

import com.agent.dock.provider.ProviderKey;

import java.time.Instant;

/** 워크스페이스(폴더)에서의 런타임 상태. 상태 행이 없으면 확인한 적이 없다는 뜻이라 DISCONNECTED 로 본다. */
public record WorkspaceRuntimeResponse(
        Long providerId, ProviderKey providerKey, String name, boolean enabled,
        String status, Instant lastCheckedAt, String lastError
) {
    public static WorkspaceRuntimeResponse from(com.agent.dock.provider.AiProvider provider, WorkspaceRuntimeStatus status) {
        return new WorkspaceRuntimeResponse(
                provider.getId(), provider.getKey(), provider.getName(), provider.isEnabled(),
                status == null ? "DISCONNECTED" : status.getStatus().name(),
                status == null ? null : status.getLastCheckedAt(),
                status == null ? null : status.getLastError()
        );
    }
}
