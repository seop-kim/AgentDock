package com.agent.dock.task.repository;

import com.agent.dock.task.domain.Task;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

    String RELATIONS = "select t from Task t join fetch t.project "
            + "left join fetch t.group left join fetch t.agent ";

    @Query(RELATIONS + "order by t.id desc")
    List<Task> findAllWithRelations();

    @Query(RELATIONS + "where t.project.id = :projectId order by t.id desc")
    List<Task> findByProjectWithRelations(@Param("projectId") Long projectId);

    @Query(RELATIONS + "where t.group.id = :groupId order by t.id desc")
    List<Task> findByGroupWithRelations(@Param("groupId") Long groupId);

    @Query(RELATIONS + "where t.id = :id")
    Optional<Task> findWithRelations(@Param("id") Long id);

    /** 프로젝트·에이전트·그룹을 지우기 전에 태스크를 지우기 위해 id 만 모은다(실행·첨부를 먼저 지워야 한다). */
    @Query("select t.id from Task t where t.project.id = :projectId")
    List<Long> findIdsByProjectId(@Param("projectId") Long projectId);

    @Query("select t.id from Task t where t.agent.id = :agentId")
    List<Long> findIdsByAgentId(@Param("agentId") Long agentId);

    @Query("select t.id from Task t where t.group.id = :groupId")
    List<Long> findIdsByGroupId(@Param("groupId") Long groupId);
}
