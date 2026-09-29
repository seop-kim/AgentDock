package com.agent.dock.execution;

import java.time.Instant;

public record ExecutionResponse(
        Long id, Long agentId, Long workspaceId, Long taskId, String prompt, ExecutionStatus status,
        Instant startedAt, Instant finishedAt, Integer exitCode, String errorMessage,
        Instant createdAt, Instant updatedAt
) {
    public static ExecutionResponse from(Execution e) {
        // 저장 직후에는 insertable=false 인 shadow FK 컬럼이 아직 채워지지 않으므로 관계에서 보완한다.
        Long agentId = e.getAgentId() != null ? e.getAgentId() : (e.getAgent() != null ? e.getAgent().getId() : null);
        Long workspaceId = e.getWorkspaceId() != null ? e.getWorkspaceId() : (e.getWorkspace() != null ? e.getWorkspace().getId() : null);
        return new ExecutionResponse(e.getId(), agentId, workspaceId, e.getTaskId(), e.getPrompt(), e.getStatus(),
                e.getStartedAt(), e.getFinishedAt(), e.getExitCode(), e.getErrorMessage(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
