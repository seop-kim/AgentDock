package com.agent.dock.project;

import com.agent.dock.workspace.WorkspaceResponse;

import java.time.Instant;

public record ProjectResponse(
        Long id, String name, String description,
        Long workspaceId, WorkspaceResponse workspace,
        Instant createdAt, Instant updatedAt
) {
    public static ProjectResponse from(Project p) {
        // 저장 직후에는 insertable=false 인 shadow FK 컬럼이 아직 채워지지 않으므로 관계에서 보완한다.
        Long workspaceId = p.getWorkspaceId() != null ? p.getWorkspaceId()
                : (p.getWorkspace() != null ? p.getWorkspace().getId() : null);
        WorkspaceResponse workspace = p.getWorkspace() == null ? null : WorkspaceResponse.from(p.getWorkspace());
        return new ProjectResponse(p.getId(), p.getName(), p.getDescription(),
                workspaceId, workspace, p.getCreatedAt(), p.getUpdatedAt());
    }
}
