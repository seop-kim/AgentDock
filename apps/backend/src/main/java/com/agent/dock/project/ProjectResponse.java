package com.agent.dock.project;

import com.agent.dock.agent.AgentSummary;

import java.time.Instant;
import java.util.List;

public record ProjectResponse(
        Long id, String name, String description,
        AgentSummary masterAgent, String masterPrompt,
        List<ProjectWorkspaceResponse> workspaces,
        Instant createdAt, Instant updatedAt
) {
    public static ProjectResponse from(Project p, List<ProjectWorkspace> links) {
        List<ProjectWorkspaceResponse> workspaces = links.stream().map(ProjectWorkspaceResponse::from).toList();
        AgentSummary master = p.getMasterAgent() == null ? null : AgentSummary.from(p.getMasterAgent());
        return new ProjectResponse(p.getId(), p.getName(), p.getDescription(), master,
                p.getMasterPrompt() == null ? "" : p.getMasterPrompt(), workspaces, p.getCreatedAt(), p.getUpdatedAt());
    }
}
