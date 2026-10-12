package com.agent.dock.budget.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 기간별 사용량 집계(GET /usage?projectId=&from=&to=&bucket=).
 *
 * 버킷은 UTC 기준으로 아래로 정렬한 구간(DAY/WEEK/MONTH)이고, total* 은 조회 구간 전체 합이다.
 * 값은 CLI 가 준 계측값만 더한다(추정 금지). projectId 가 없으면 전 프로젝트 합이다.
 */
public record UsageResponse(
        Long projectId,
        Instant from,
        Instant to,
        String bucket,
        BigDecimal totalCostUsd,
        long totalInputTokens,
        long totalOutputTokens,
        long executionCount,
        List<UsageBucket> buckets
) {
    /** 한 구간의 사용량. start(포함) ~ end(미포함). */
    public record UsageBucket(
            Instant start,
            Instant end,
            long executionCount,
            BigDecimal costUsd,
            long inputTokens,
            long outputTokens
    ) {
    }
}