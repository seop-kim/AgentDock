package com.agent.dock.execution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {
    Optional<Execution> findFirstByTaskIdOrderByIdDesc(Long taskId);
}
