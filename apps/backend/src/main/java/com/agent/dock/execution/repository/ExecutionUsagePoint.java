package com.agent.dock.execution.repository;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 기간별 사용량 집계용 최소 투영. 실행 하나의 생성시각과 계측값(비용·입출력 토큰)만 읽어,
 * 무거운 텍스트(prompt·result_text·handoff)를 DB 에서 끌어오지 않는다.
 */
public record ExecutionUsagePoint(
        Instant createdAt,
        BigDecimal costUsd,
        Integer inputTokens,
        Integer outputTokens
) {
}