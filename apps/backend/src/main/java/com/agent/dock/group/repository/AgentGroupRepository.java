package com.agent.dock.group.repository;

import com.agent.dock.group.domain.AgentGroup;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentGroupRepository extends JpaRepository<AgentGroup, Long> {

    boolean existsByProjectIdAndName(Long projectId, String name);

    /** 프로젝트의 그룹 전부(구성도 위치 초기화·프로젝트 삭제에 쓴다). */
    List<AgentGroup> findByProjectId(Long projectId);

    @Query("select g from AgentGroup g join fetch g.project left join fetch g.leaderAgent order by g.name asc")
    List<AgentGroup> findAllWithRelations();

    @Query("select g from AgentGroup g join fetch g.project left join fetch g.leaderAgent "
            + "where g.project.id = :projectId order by g.name asc")
    List<AgentGroup> findByProjectWithRelations(@Param("projectId") Long projectId);

    @Query("select g from AgentGroup g join fetch g.project left join fetch g.leaderAgent where g.id = :id")
    Optional<AgentGroup> findWithRelations(@Param("id") Long id);
}
