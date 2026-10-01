package com.agent.dock.execution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExecutionLogRepository extends JpaRepository<ExecutionLog, Long> {
    List<ExecutionLog> findByExecutionIdOrderByCreatedAtAsc(Long executionId);

    long countByExecutionId(Long executionId);
}
