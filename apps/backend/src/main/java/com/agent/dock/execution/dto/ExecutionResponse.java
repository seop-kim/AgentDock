package com.agent.dock.execution.dto;

import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.domain.ExecutionDecision;
import com.agent.dock.execution.domain.ExecutionStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record ExecutionResponse(
        Long id, Long agentId, Long workspaceId, Long taskId, String prompt, ExecutionStatus status,
        Instant startedAt, Instant finishedAt, Integer exitCode, String errorMessage,
        String resultText, ExecutionDecision decision, Long delegatedTargetAgentId,
        Long parentExecutionId, Long rootExecutionId,
        Integer inputTokens, Integer outputTokens, Integer cacheReadTokens, Integer cacheCreationTokens,
        BigDecimal costUsd, Long durationMs, Integer numTurns, String sessionId,
        Instant createdAt, Instant updatedAt
) {
    public static ExecutionResponse from(Execution e) {
        // 저장 직후에는 insertable=false 인 shadow FK 컬럼이 아직 채워지지 않으므로 관계에서 보완한다.
        Long agentId = e.getAgentId() != null ? e.getAgentId() : (e.getAgent() != null ? e.getAgent().getId() : null);
        Long workspaceId = e.getWorkspaceId() != null ? e.getWorkspaceId() : (e.getWorkspace() != null ? e.getWorkspace().getId() : null);
        Long delegatedTargetAgentId = e.getDelegatedTargetAgentId() != null ? e.getDelegatedTargetAgentId()
                : (e.getDelegatedTargetAgent() != null ? e.getDelegatedTargetAgent().getId() : null);
        Long parentExecutionId = e.getParentExecutionId() != null ? e.getParentExecutionId()
                : (e.getParentExecution() != null ? e.getParentExecution().getId() : null);
        return new ExecutionResponse(
                e.getId(), agentId, workspaceId, e.getTaskId(), e.getPrompt(), e.getStatus(),
                e.getStartedAt(), e.getFinishedAt(), e.getExitCode(), e.getErrorMessage(),
                e.getResultText(), e.getDecision(), delegatedTargetAgentId, parentExecutionId, e.getRootExecutionId(),
                e.getInputTokens(), e.getOutputTokens(), e.getCacheReadTokens(), e.getCacheCreationTokens(),
                e.getCostUsd(), e.getDurationMs(), e.getNumTurns(), e.getSessionId(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
