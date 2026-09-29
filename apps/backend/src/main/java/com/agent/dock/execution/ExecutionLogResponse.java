package com.agent.dock.execution;

import java.time.Instant;

public record ExecutionLogResponse(Long id, Long executionId, LogStream stream, String content, Instant createdAt) {
    public static ExecutionLogResponse from(ExecutionLog log) {
        return new ExecutionLogResponse(log.getId(), log.getExecutionId(), log.getStream(), log.getContent(), log.getCreatedAt());
    }
}
