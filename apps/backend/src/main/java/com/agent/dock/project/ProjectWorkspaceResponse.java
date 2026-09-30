package com.agent.dock.project;

import com.agent.dock.workspace.Workspace;

import java.time.Instant;

public record ProjectWorkspaceResponse(Long workspaceId, String name, String path, boolean isDefault) {
    public static ProjectWorkspaceResponse from(ProjectWorkspace link) {
        Workspace workspace = link.getWorkspace();
        return new ProjectWorkspaceResponse(workspace.getId(), workspace.getName(), workspace.getPath(), link.isDefault());
    }
}
