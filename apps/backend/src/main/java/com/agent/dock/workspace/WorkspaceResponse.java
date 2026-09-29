package com.agent.dock.workspace;

import java.time.Instant;

public record WorkspaceResponse(Long id, String name, String path, String description, Instant createdAt, Instant updatedAt) {
    public static WorkspaceResponse from(Workspace w) {
        return new WorkspaceResponse(w.getId(), w.getName(), w.getPath(), w.getDescription(), w.getCreatedAt(), w.getUpdatedAt());
    }
}
