package com.agent.dock.workspace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkspaceRuntimeStatusRepository extends JpaRepository<WorkspaceRuntimeStatus, Long> {
    List<WorkspaceRuntimeStatus> findByWorkspaceId(Long workspaceId);

    Optional<WorkspaceRuntimeStatus> findByWorkspaceIdAndProviderId(Long workspaceId, Long providerId);
}
