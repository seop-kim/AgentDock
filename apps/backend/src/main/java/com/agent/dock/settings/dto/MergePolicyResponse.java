package com.agent.dock.settings.dto;

import com.agent.dock.settings.domain.MergePolicy;
import java.time.Instant;

/**
 * 병합 정책 응답(GET/PUT /settings/merge-policy). 엔티티를 그대로 싣지 않고 원시값만 담는다.
 */
public record MergePolicyResponse(
        boolean autoCleanupEnabled,
        boolean cleanupDeleteBranch,
        Instant updatedAt
) {
    public static MergePolicyResponse from(MergePolicy policy) {
        return new MergePolicyResponse(policy.isAutoCleanupEnabled(), policy.isCleanupDeleteBranch(),
                policy.getUpdatedAt());
    }
}