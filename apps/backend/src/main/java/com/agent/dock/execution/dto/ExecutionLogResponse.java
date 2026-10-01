package com.agent.dock.execution.dto;

import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.domain.ExecutionLog;
import com.agent.dock.execution.domain.LogStream;
import java.time.Instant;

public record ExecutionLogResponse(Long id, Long executionId, LogStream stream, String content, Instant createdAt) {
    public static ExecutionLogResponse from(ExecutionLog log) {
        return new ExecutionLogResponse(log.getId(), log.getExecutionId(), log.getStream(), log.getContent(), log.getCreatedAt());
    }
}
