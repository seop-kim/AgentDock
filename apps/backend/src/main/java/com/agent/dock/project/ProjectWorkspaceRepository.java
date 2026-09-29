package com.agent.dock.project;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProjectWorkspaceRepository extends JpaRepository<ProjectWorkspace, Long> {
    @EntityGraph(attributePaths = "workspace")
    List<ProjectWorkspace> findByProjectIdIn(Collection<Long> projectIds);

    @EntityGraph(attributePaths = "workspace")
    List<ProjectWorkspace> findByProjectIdOrderByIdAsc(Long projectId);

    @EntityGraph(attributePaths = "workspace")
    Optional<ProjectWorkspace> findFirstByProjectIdAndIsDefaultTrue(Long projectId);

    @EntityGraph(attributePaths = "workspace")
    Optional<ProjectWorkspace> findFirstByProjectIdOrderByIdAsc(Long projectId);

    boolean existsByProjectIdAndWorkspaceId(Long projectId, Long workspaceId);

    long countByProjectId(Long projectId);
}
