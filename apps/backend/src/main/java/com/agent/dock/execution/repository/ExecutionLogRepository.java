package com.agent.dock.execution.repository;

import com.agent.dock.execution.domain.ExecutionLog;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExecutionLogRepository extends JpaRepository<ExecutionLog, Long> {
    List<ExecutionLog> findByExecutionIdOrderByCreatedAtAsc(Long executionId);

    long countByExecutionId(Long executionId);

    /** 실행을 지우기 전에 로그 행을 먼저 지운다(FK 안전). */
    @Modifying
    @Query("delete from ExecutionLog l where l.executionId in :executionIds")
    void deleteByExecutionIdIn(@Param("executionIds") Collection<Long> executionIds);
}
