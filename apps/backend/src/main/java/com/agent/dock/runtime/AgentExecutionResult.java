package com.agent.dock.runtime;

import java.util.Map;

/**
 * 한 스텝(CLI 한 번)의 결과.
 * resultText 는 모델이 낸 최종 텍스트(계약을 강제했으면 그 JSON), structured 는 그 JSON 을 파싱한 값이다.
 */
public record AgentExecutionResult(
        int exitCode,
        String resultText,
        Map<String, Object> structured,
        ExecutionMetrics metrics,
        boolean isError
) {
    public static AgentExecutionResult failed(int exitCode) {
        return new AgentExecutionResult(exitCode, null, null, ExecutionMetrics.empty(), true);
    }

    public boolean succeeded() {
        return exitCode == 0 && !isError;
    }
}
