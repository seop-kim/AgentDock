package com.agent.dock.execution.repository;

import com.agent.dock.execution.domain.Execution;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExecutionRepository extends JpaRepository<Execution, Long> {
    Optional<Execution> findFirstByTaskIdOrderByIdDesc(Long taskId);

    long countByRootExecutionId(Long rootExecutionId);

    List<Execution> findByRootExecutionIdOrderByIdAsc(Long rootExecutionId);

    @Query("select coalesce(sum(e.costUsd), 0) from Execution e where e.rootExecutionId = :rootExecutionId")
    BigDecimal sumCostByRootExecutionId(@Param("rootExecutionId") Long rootExecutionId);

    /** 에이전트·태스크 삭제 전에 실행을 지우기 위해 id 만 모은다(실행 로그를 먼저 지워야 한다). */
    @Query("select e.id from Execution e where e.agentId = :agentId")
    List<Long> findIdsByAgentId(@Param("agentId") Long agentId);

    @Query("select e.id from Execution e where e.agentId in :agentIds")
    List<Long> findIdsByAgentIdIn(@Param("agentIds") Collection<Long> agentIds);

    @Query("select e.id from Execution e where e.taskId in :taskIds")
    List<Long> findIdsByTaskIdIn(@Param("taskIds") Collection<Long> taskIds);

    /**
     * 이 에이전트를 위임 대상으로 가리키는 실행의 참조를 비운다(에이전트 삭제 시 FK 위반 방지).
     * 위임한 실행 자체는 남겨 누구에게 맡겼는지의 기록만 지운다.
     */
    @Modifying
    @Query("update Execution e set e.delegatedTargetAgent = null where e.delegatedTargetAgent.id = :agentId")
    void clearDelegatedTarget(@Param("agentId") Long agentId);
}
