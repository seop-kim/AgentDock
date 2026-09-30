package com.agent.dock.workspace;

import com.agent.dock.provider.AiProvider;

import java.time.Instant;
import java.util.List;

/** 워크스페이스(폴더)에서의 런타임 상태. 상태 행이 없으면 확인한 적이 없다는 뜻이라 DISCONNECTED 로 본다. */
public record WorkspaceRuntimeResponse(
        Long providerId, String providerKey, String name, boolean enabled,
        String status, boolean cliMissing,
        List<String> install, String installRequire,
        Instant lastCheckedAt, String lastError
) {
    public static WorkspaceRuntimeResponse from(AiProvider provider, WorkspaceRuntimeStatus status) {
        return new WorkspaceRuntimeResponse(
                provider.getId(), provider.getKey().name(), provider.getName(), provider.isEnabled(),
                status == null ? "DISCONNECTED" : status.getStatus().name(),
                status != null && status.isCliMissing(),
                stringList(provider, "install"),
                stringValue(provider, "installRequire"),
                status == null ? null : status.getLastCheckedAt(),
                status == null ? null : status.getLastError()
        );
    }

    private static List<String> stringList(AiProvider provider, String key) {
        Object value = provider.getCapabilities() == null ? null : provider.getCapabilities().get(key);
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return List.of(String.valueOf(value));
    }

    private static String stringValue(AiProvider provider, String key) {
        Object value = provider.getCapabilities() == null ? null : provider.getCapabilities().get(key);
        return value == null ? null : String.valueOf(value);
    }
}
