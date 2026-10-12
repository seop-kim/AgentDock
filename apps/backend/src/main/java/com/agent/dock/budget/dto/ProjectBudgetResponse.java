package com.agent.dock.budget.dto;

import com.agent.dock.budget.domain.BudgetPeriod;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 프로젝트 예산과 이번 기간 사용 현황(GET /projects/{id}/budget).
 *
 * configured=false 면 아직 예산을 정하지 않은 것이다(amountUsd·period·ratio 는 null, usedUsd 는 이번 달 기준).
 * ratio = usedUsd / amountUsd 이고 warning 은 ratio >= alertRatio 일 때 true 다(80% 도달 경고).
 */
public record ProjectBudgetResponse(
        Long projectId,
        boolean configured,
        BigDecimal amountUsd,
        BudgetPeriod period,
        BigDecimal alertRatio,
        BigDecimal usedUsd,
        BigDecimal ratio,
        boolean warning,
        Instant periodStart,
        Instant periodEnd,
        long executionCount,
        long inputTokens,
        long outputTokens
) {
}