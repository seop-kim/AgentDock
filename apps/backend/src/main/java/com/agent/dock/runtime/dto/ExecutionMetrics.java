package com.agent.dock.runtime.dto;

import java.math.BigDecimal;

/**
 * CLI 가 돌려준 계측값. 값은 추정하지 않고 CLI `--output-format json/stream-json` 의
 * `usage` / `total_cost_usd` / `duration_ms` / `num_turns` / `session_id` 에서만 가져온다.
 */
public record ExecutionMetrics(
        Integer inputTokens,
        Integer outputTokens,
        Integer cacheReadTokens,
        Integer cacheCreationTokens,
        BigDecimal costUsd,
        Long durationMs,
        Integer numTurns,
        String sessionId
) {
    public static ExecutionMetrics empty() {
        return new ExecutionMetrics(null, null, null, null, null, null, null, null);
    }
}
