package com.agent.dock.provider.dto;

import com.agent.dock.provider.domain.ProviderKey;

/**
 * GET /ai-providers/cli — 런타임별 CLI 상태 한 줄. 카드마다 CLI 를 실행하지 않게 한 번에 조회한다.
 * 저장하지 않고 조회 시점에 계산한다(호출마다 CLI 를 실행한다).
 */
public record ProviderCliStatusResponse(
        Long providerId, ProviderKey key, String name, boolean enabled,
        String resolvedPath, String version, boolean runnable, String detail) {
}
