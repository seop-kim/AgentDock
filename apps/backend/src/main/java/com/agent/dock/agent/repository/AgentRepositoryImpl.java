package com.agent.dock.agent.repository;

import com.agent.dock.agent.domain.Agent;
import com.agent.dock.agent.domain.QAgent;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AgentRepositoryImpl implements AgentRepositoryCustom {
    private final JPAQueryFactory queryFactory;

    @Override
    public List<Agent> findAllWithRelations() {
        QAgent agent = QAgent.agent;
        return queryFactory.selectFrom(agent)
                .leftJoin(agent.project).fetchJoin()
                .leftJoin(agent.role).fetchJoin()
                .leftJoin(agent.permissionProfile).fetchJoin()
                .leftJoin(agent.provider).fetchJoin()
                .orderBy(agent.name.asc())
                .fetch();
    }

    @Override
    public Optional<Agent> findByIdWithRelations(Long id) {
        QAgent agent = QAgent.agent;
        Agent result = queryFactory.selectFrom(agent)
                .leftJoin(agent.project).fetchJoin()
                .leftJoin(agent.role).fetchJoin()
                .leftJoin(agent.permissionProfile).fetchJoin()
                .leftJoin(agent.provider).fetchJoin()
                .where(agent.id.eq(id))
                .fetchOne();
        return Optional.ofNullable(result);
    }
}
