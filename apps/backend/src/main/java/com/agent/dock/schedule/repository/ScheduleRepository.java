package com.agent.dock.schedule.repository;

import com.agent.dock.schedule.domain.Schedule;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {

    String RELATIONS = "select s from Schedule s join fetch s.project "
            + "left join fetch s.targetAgent left join fetch s.targetGroup ";

    @Query(RELATIONS + "order by s.name asc")
    List<Schedule> findAllWithRelations();

    @Query(RELATIONS + "where s.project.id = :projectId order by s.name asc")
    List<Schedule> findByProjectWithRelations(@Param("projectId") Long projectId);

    @Query(RELATIONS + "where s.id = :id")
    Optional<Schedule> findWithRelations(@Param("id") Long id);

    /** 지금 실행할 때가 된(켜져 있고 next_run_at 이 지난) 예약 id. */
    @Query("select s.id from Schedule s where s.enabled = true and s.nextRunAt <= :now order by s.nextRunAt asc")
    List<Long> findDueIds(@Param("now") Instant now);
}
