package com.agent.dock.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgentGroupRepository extends JpaRepository<AgentGroup, Long> {

    boolean existsByProjectIdAndName(Long projectId, String name);

    @Query("select g from AgentGroup g join fetch g.project left join fetch g.leaderAgent order by g.name asc")
    List<AgentGroup> findAllWithRelations();

    @Query("select g from AgentGroup g join fetch g.project left join fetch g.leaderAgent "
            + "where g.project.id = :projectId order by g.name asc")
    List<AgentGroup> findByProjectWithRelations(@Param("projectId") Long projectId);

    @Query("select g from AgentGroup g join fetch g.project left join fetch g.leaderAgent where g.id = :id")
    Optional<AgentGroup> findWithRelations(@Param("id") Long id);
}
