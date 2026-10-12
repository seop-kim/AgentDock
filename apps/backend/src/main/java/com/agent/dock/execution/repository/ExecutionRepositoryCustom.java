package com.agent.dock.execution.repository;

import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.domain.ExecutionStatus;
import java.util.Collection;
import java.util.List;

/** 실행 목록(대시보드·Tasks·요청 창)용 동적 조회. */
public interface ExecutionRepositoryCustom {

    /**
     * 실행 목록을 최신순(id desc)으로 조회한다.
     *
     * @param projectId 있으면 그 프로젝트(execution.task_id → task.project_id) 실행만. null 이면 전 프로젝트.
     * @param statuses  있으면 그 상태들만(OR). 비었으면 전 상태.
     * @param limit     최대 개수.
     * @param beforeId  있으면 그 id 보다 오래된 것만(커서).
     */
    List<Execution> searchSummaries(Long projectId, Collection<ExecutionStatus> statuses, int limit, Long beforeId);
}
