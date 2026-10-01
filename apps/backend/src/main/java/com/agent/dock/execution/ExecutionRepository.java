package com.agent.dock.execution;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {
    Optional<Execution> findFirstByTaskIdOrderByIdDesc(Long taskId);

    long countByRootExecutionId(Long rootExecutionId);

    List<Execution> findByRootExecutionIdOrderByIdAsc(Long rootExecutionId);

    @Query("select coalesce(sum(e.costUsd), 0) from Execution e where e.rootExecutionId = :rootExecutionId")
    BigDecimal sumCostByRootExecutionId(@Param("rootExecutionId") Long rootExecutionId);
}
