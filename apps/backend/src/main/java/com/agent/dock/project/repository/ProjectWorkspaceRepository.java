package com.agent.dock.project.repository;

import com.agent.dock.project.domain.Project;
import com.agent.dock.project.domain.ProjectWorkspace;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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

    /** 프로젝트 삭제 전에 할당을 먼저 지운다. */
    void deleteByProjectId(Long projectId);
}
