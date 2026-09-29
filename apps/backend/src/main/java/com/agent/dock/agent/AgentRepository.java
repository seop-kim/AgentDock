package com.agent.dock.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentRepository extends JpaRepository<Agent, Long>, AgentRepositoryCustom {

    /** Provider 의 Connection 을 삭제하기 전에 Agent 의 참조를 끊는다(FK 위반 방지). */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Agent a set a.connection = null "
            + "where a.connection.id in (select c.id from AiConnection c where c.provider.id = :providerId)")
    int detachConnectionsOfProvider(@Param("providerId") Long providerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Agent a set a.connection = null where a.connection.id = :connectionId")
    int detachConnection(@Param("connectionId") Long connectionId);
}
