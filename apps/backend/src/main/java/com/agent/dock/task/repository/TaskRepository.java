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
}
