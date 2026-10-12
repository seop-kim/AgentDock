package com.agent.dock.settings.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 병합 정책 교체(PUT /settings/merge-policy). 전체 교체라 두 값 모두 필수다 — 누락하면 400.
 */
public record MergePolicyRequest(
        @NotNull Boolean autoCleanupEnabled,
        @NotNull Boolean cleanupDeleteBranch
) {
}