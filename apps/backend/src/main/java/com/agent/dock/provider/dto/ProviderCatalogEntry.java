package com.agent.dock.provider.dto;

import com.agent.dock.provider.domain.ProviderKey;
import java.util.Map;

/**
 * GET /ai-providers/catalog — 등록 가능한 런타임과 기본값.
 * registered=true 면 이미 등록되어 있어 다시 등록할 수 없다(POST /ai-providers 가 409).
 */
public record ProviderCatalogEntry(ProviderKey key, String name, boolean registered, Map<String, Object> capabilities) {
}
