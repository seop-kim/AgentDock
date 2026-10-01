package com.agent.dock.workspace.repository;

import com.agent.dock.workspace.domain.Workspace;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceRepository extends JpaRepository<Workspace, Long> {
    List<Workspace> findAllByOrderByNameAsc();
}
