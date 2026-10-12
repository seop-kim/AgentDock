package com.agent.dock.execution.repository;

import com.agent.dock.execution.domain.ExecutionStatus;
import java.math.BigDecimal;

/**
 * 실행 집계용 최소 투영. 무거운 텍스트(prompt·resultText·handoff)를 읽지 않으려고 JPQL 생성자 표현식으로 만든다.
 * projectId·groupId 는 task_id 로 Task 를 따로 읽어 붙인다(실행 패키지가 task 패키지에 의존하지 않게).
 */
public record ExecutionUsage(
        Long taskId,
        ExecutionStatus status,
        BigDecimal costUsd,
        Integer inputTokens,
        Integer outputTokens
) {
}