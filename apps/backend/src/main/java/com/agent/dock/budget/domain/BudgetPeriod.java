package com.agent.dock.budget.domain;

/** 예산 집계 기간. 기간별 사용량의 창(window)과 사용량 버킷 단위가 이 값을 따른다. */
public enum BudgetPeriod {
    DAILY,
    WEEKLY,
    MONTHLY
}