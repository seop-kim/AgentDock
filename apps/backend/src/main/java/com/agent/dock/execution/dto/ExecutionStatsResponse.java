package com.agent.dock.execution.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 실행 집계(GET /executions/summary). 대시보드가 전역/프로젝트별/팀별 실행 수·비용·입출력 토큰을 한 번에 읽는다.
 *
 * 팀별 묶음은 실행이 속한 Task 의 group_id(task.group_id) 기준이다 — 그룹에 맡긴 Task 의 트리 전체가 한 팀으로 잡힌다.
 * 값은 CLI 가 준 계측값만 더한다(추정 금지). 없으면 0 이다.
 */
public record ExecutionStatsResponse(
        long executionCount,
        long runningCount,
        BigDecimal totalCostUsd,
        long totalInputTokens,
        long totalOutputTokens,
        List<ProjectUsage> byProject,
        List<GroupUsage> byGroup
) {
    public record ProjectUsage(
            Long projectId,
            String projectName,
            long executionCount,
            BigDecimal costUsd,
            long inputTokens,
            long outputTokens
    ) {
    }

    public record GroupUsage(
            Long groupId,
            String groupName,
            Long projectId,
            long executionCount,
            BigDecimal costUsd,
            long inputTokens,
            long outputTokens
    ) {
    }

    public static ExecutionStatsResponse empty() {
        return new ExecutionStatsResponse(0L, 0L, BigDecimal.ZERO, 0L, 0L, List.of(), List.of());
    }
}