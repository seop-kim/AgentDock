package com.agent.dock.execution.repository;

import com.agent.dock.execution.domain.Execution;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {
    Optional<Execution> findFirstByTaskIdOrderByIdDesc(Long taskId);

    long countByRootExecutionId(Long rootExecutionId);

    List<Execution> findByRootExecutionIdOrderByIdAsc(Long rootExecutionId);

    @Query("select coalesce(sum(e.costUsd), 0) from Execution e where e.rootExecutionId = :rootExecutionId")
    BigDecimal sumCostByRootExecutionId(@Param("rootExecutionId") Long rootExecutionId);
}
