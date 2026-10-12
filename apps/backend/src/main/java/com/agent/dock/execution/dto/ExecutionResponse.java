package com.agent.dock.execution.dto;

import com.agent.dock.execution.domain.ChangedFile;
import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.domain.ExecutionDecision;
import com.agent.dock.execution.domain.ExecutionStatus;
import com.agent.dock.execution.domain.MergeStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record ExecutionResponse(
        Long id, Long agentId, Long workspaceId, Long taskId, String prompt, ExecutionStatus status,
        Instant startedAt, Instant finishedAt, Integer exitCode, String errorMessage,
        String resultText, ExecutionDecision decision, Long delegatedTargetAgentId,
        Long parentExecutionId, Long rootExecutionId,
        String worktreePath, String worktreeBranch,
        String resultCommit, MergeStatus mergeStatus, String mergeDetail, List<ChangedFile> changedFiles,
        // question/answer: 판단 실행이 사람에게 물은 질문과 그 답(상태가 WAITING_INPUT 이면 답을 기다린다).
        // questionOptions: 그 질문에 함께 온 보기(화면이 버튼으로 그린다). 없으면 빈 목록.
        String question, String answer, List<String> questionOptions,
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
                e.getWorktreePath(), e.getWorktreeBranch(),
                e.getResultCommit(), e.getMergeStatus(), e.getMergeDetail(),
                e.getChangedFiles() == null ? List.of() : e.getChangedFiles(),
                e.getQuestion(), e.getAnswer(),
                e.getQuestionOptions() == null ? List.of() : e.getQuestionOptions(),
                e.getInputTokens(), e.getOutputTokens(), e.getCacheReadTokens(), e.getCacheCreationTokens(),
                e.getCostUsd(), e.getDurationMs(), e.getNumTurns(), e.getSessionId(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
