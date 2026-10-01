package com.agent.dock.workspace.repository;

import com.agent.dock.workspace.domain.Workspace;
import com.agent.dock.workspace.domain.WorkspaceRuntimeStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRuntimeStatusRepository extends JpaRepository<WorkspaceRuntimeStatus, Long> {
    List<WorkspaceRuntimeStatus> findByWorkspaceId(Long workspaceId);

    List<WorkspaceRuntimeStatus> findByWorkspaceIdIn(Collection<Long> workspaceIds);

    Optional<WorkspaceRuntimeStatus> findByWorkspaceIdAndProviderId(Long workspaceId, Long providerId);
}
