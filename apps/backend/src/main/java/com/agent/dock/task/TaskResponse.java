package com.agent.dock.task;

import com.agent.dock.agent.AgentSummary;
import com.agent.dock.group.GroupSummary;
import com.agent.dock.project.ProjectSummary;

import java.time.Instant;

public record TaskResponse(
        Long id,
        ProjectSummary project,
        GroupSummary group,
        AgentSummary agent,
        String title, String prompt, TaskStatus status,
        Long latestExecutionId,
        Instant createdAt, Instant updatedAt
) {
    public static TaskResponse from(Task t, Long latestExecutionId) {
        ProjectSummary project = t.getProject() == null ? null : ProjectSummary.from(t.getProject());
        GroupSummary group = t.getGroup() == null ? null : GroupSummary.from(t.getGroup());
        AgentSummary agent = t.getAgent() == null ? null : AgentSummary.from(t.getAgent());
        return new TaskResponse(t.getId(), project, group, agent, t.getTitle(), t.getPrompt(), t.getStatus(),
                latestExecutionId, t.getCreatedAt(), t.getUpdatedAt());
    }
}
