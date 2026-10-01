package com.agent.dock.execution.repository;

import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.domain.ExecutionLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionLogRepository extends JpaRepository<ExecutionLog, Long> {
    List<ExecutionLog> findByExecutionIdOrderByCreatedAtAsc(Long executionId);

    long countByExecutionId(Long executionId);
}
