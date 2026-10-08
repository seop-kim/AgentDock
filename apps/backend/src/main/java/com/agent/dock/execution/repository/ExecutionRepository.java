package com.agent.dock.execution.repository;

import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.domain.ExecutionStatus;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExecutionRepository extends JpaRepository<Execution, Long>, ExecutionRepositoryCustom {
    Optional<Execution> findFirstByTaskIdOrderByIdDesc(Long taskId);

    long countByRootExecutionId(Long rootExecutionId);

    List<Execution> findByRootExecutionIdOrderByIdAsc(Long rootExecutionId);

    @Query("select coalesce(sum(e.costUsd), 0) from Execution e where e.rootExecutionId = :rootExecutionId")
    BigDecimal sumCostByRootExecutionId(@Param("rootExecutionId") Long rootExecutionId);

    @Query("select e.id from Execution e where e.agentId = :agentId")
    List<Long> findIdsByAgentId(@Param("agentId") Long agentId);

    @Query("select e.id from Execution e where e.agentId in :agentIds")
    List<Long> findIdsByAgentIdIn(@Param("agentIds") Collection<Long> agentIds);

    @Query("select e.id from Execution e where e.taskId in :taskIds")
    List<Long> findIdsByTaskIdIn(@Param("taskIds") Collection<Long> taskIds);

    @Modifying
    @Query("update Execution e set e.delegatedTargetAgent = null where e.delegatedTargetAgent.id = :agentId")
    void clearDelegatedTarget(@Param("agentId") Long agentId);

    /** 집계용(전역) — 무거운 텍스트를 빼고 계측값만 읽는다. */
    @Query("select new com.agent.dock.execution.repository.ExecutionUsage("
            + "e.taskId, e.status, e.costUsd, e.inputTokens, e.outputTokens) from Execution e")
    List<ExecutionUsage> findUsageGlobal();

    /** 집계용(프로젝트별). */
    @Query("select new com.agent.dock.execution.repository.ExecutionUsage("
            + "e.taskId, e.status, e.costUsd, e.inputTokens, e.outputTokens) from Execution e "
            + "where e.taskId in :taskIds")
    List<ExecutionUsage> findUsageByTaskIds(@Param("taskIds") Collection<Long> taskIds);
}