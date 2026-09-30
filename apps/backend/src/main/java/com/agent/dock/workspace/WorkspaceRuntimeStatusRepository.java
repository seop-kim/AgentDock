package com.agent.dock.workspace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkspaceRuntimeStatusRepository extends JpaRepository<WorkspaceRuntimeStatus, Long> {
    List<WorkspaceRuntimeStatus> findByWorkspaceId(Long workspaceId);

    List<WorkspaceRuntimeStatus> findByWorkspaceIdIn(Collection<Long> workspaceIds);

    Optional<WorkspaceRuntimeStatus> findByWorkspaceIdAndProviderId(Long workspaceId, Long providerId);
}
