package com.agent.dock.project.dto;

import com.agent.dock.project.domain.Project;
import com.agent.dock.project.domain.ProjectWorkspace;
import com.agent.dock.workspace.domain.Workspace;
import java.time.Instant;

public record ProjectWorkspaceResponse(Long workspaceId, String name, String path, boolean isDefault) {
    public static ProjectWorkspaceResponse from(ProjectWorkspace link) {
        Workspace workspace = link.getWorkspace();
        return new ProjectWorkspaceResponse(workspace.getId(), workspace.getName(), workspace.getPath(), link.isDefault());
    }
}
