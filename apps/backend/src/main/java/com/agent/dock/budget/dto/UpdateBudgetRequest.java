package com.agent.dock.budget.dto;

import com.agent.dock.budget.domain.BudgetPeriod;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 프로젝트 예산 설정(PUT /projects/{id}/budget). amountUsd 는 필수(0 초과), period 는 생략하면 MONTHLY,
 * alertRatio 는 생략하면 0.80(경고 기준 비율, 0.01~1.00).
 */
public record UpdateBudgetRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amountUsd,
        BudgetPeriod period,
        @DecimalMin("0.01") @DecimalMax("1.00") BigDecimal alertRatio
) {
}