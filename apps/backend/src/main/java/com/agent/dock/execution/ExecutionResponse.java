package com.agent.dock.execution;

import java.time.Instant;

public record ExecutionResponse(
        Long id, Long agentId, Long workspaceId, String prompt, ExecutionStatus status,
        Instant startedAt, Instant finishedAt, Integer exitCode, String errorMessage,
        Instant createdAt, Instant updatedAt
) {
    public static ExecutionResponse from(Execution e) {
        return new ExecutionResponse(e.getId(), e.getAgentId(), e.getWorkspaceId(), e.getPrompt(), e.getStatus(),
                e.getStartedAt(), e.getFinishedAt(), e.getExitCode(), e.getErrorMessage(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
