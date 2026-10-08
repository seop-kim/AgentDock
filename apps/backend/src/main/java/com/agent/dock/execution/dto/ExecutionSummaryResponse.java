package com.agent.dock.execution.dto;

import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.domain.ExecutionDecision;
import com.agent.dock.execution.domain.ExecutionStatus;
import com.agent.dock.execution.domain.MergeStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 실행 목록 한 줄(GET /executions). 대시보드 집계·Tasks 목록·요청 창이 여러 행을 한 번에 읽는다.
 *
 * ExecutionResponse(상세·트리)를 재사용하지 않는다 — 목록에 필요 없는 큰 텍스트(prompt·resultText·handoff·
 * sessionId)를 싣지 않고, 반대로 상세 응답에 없는 파생 라벨(projectId·taskTitle·agentName·groupName)을 싣는다.
 * projectId·taskTitle 은 execution.task_id -> task 에서, groupName 은 task.group_id -> agent_group 에서 파생한다.
 */
public record ExecutionSummaryResponse(
        Long id,
        Long projectId,
        Long taskId,
        String taskTitle,
        Long agentId,
        String agentName,
        Long groupId,
        String groupName,
        Long rootExecutionId,
        Long parentExecutionId,
        ExecutionStatus status,
        ExecutionDecision decision,
        Long delegatedTargetAgentId,
        String question,
        List<String> questionOptions,
        String worktreeBranch,
        String resultCommit,
        MergeStatus mergeStatus,
        String mergeDetail,
        BigDecimal costUsd,
        Integer inputTokens,
        Integer outputTokens,
        Integer cacheReadTokens,
        Integer cacheCreationTokens,
        Long durationMs,
        Integer numTurns,
        Integer exitCode,
        Instant startedAt,
        Instant finishedAt,
        Instant createdAt,
        Instant updatedAt
) {
    public static ExecutionSummaryResponse from(Execution e, Long projectId, String taskTitle,
                                                String agentName, Long groupId, String groupName) {
        return new ExecutionSummaryResponse(
                e.getId(), projectId, e.getTaskId(), taskTitle, e.getAgentId(), agentName, groupId, groupName,
                e.getRootExecutionId(), e.getParentExecutionId(), e.getStatus(), e.getDecision(),
                e.getDelegatedTargetAgentId(), e.getQuestion(),
                e.getQuestionOptions() == null ? List.of() : e.getQuestionOptions(),
                e.getWorktreeBranch(), e.getResultCommit(), e.getMergeStatus(), e.getMergeDetail(),
                e.getCostUsd(), e.getInputTokens(), e.getOutputTokens(), e.getCacheReadTokens(),
                e.getCacheCreationTokens(), e.getDurationMs(), e.getNumTurns(), e.getExitCode(),
                e.getStartedAt(), e.getFinishedAt(), e.getCreatedAt(), e.getUpdatedAt());
    }
}
