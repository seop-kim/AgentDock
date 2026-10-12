package com.agent.dock.execution.repository;

import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.domain.ExecutionStatus;
import com.agent.dock.execution.domain.QExecution;
import com.agent.dock.task.domain.QTask;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * QueryDSL 로 실행 목록을 조회한다. execution 에 project_id 컬럼이 없어 프로젝트 필터는
 * task_id → task.project_id 조인으로 판별한다(엔티티 관계를 만들지 않고 조회에서만 조인한다).
 */
@RequiredArgsConstructor
public class ExecutionRepositoryImpl implements ExecutionRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    @Override
    public List<Execution> searchSummaries(Long projectId, Collection<ExecutionStatus> statuses, int limit, Long beforeId) {
        QExecution execution = QExecution.execution;
        QTask task = QTask.task;

        var query = queryFactory.selectFrom(execution);
        if (projectId != null) {
            query.leftJoin(task).on(task.id.eq(execution.taskId)).where(task.project.id.eq(projectId));
        }
        if (statuses != null && !statuses.isEmpty()) {
            query.where(execution.status.in(statuses));
        }
        if (beforeId != null) {
            query.where(execution.id.lt(beforeId));
        }
        return query.orderBy(execution.id.desc()).limit(limit).fetch();
    }
}
