package com.agent.dock.group;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AgentGroupMemberRepository extends JpaRepository<AgentGroupMember, Long> {

    @Query("select m from AgentGroupMember m join fetch m.agent a where m.group.id = :groupId order by a.name asc")
    List<AgentGroupMember> findMembersWithAgent(@Param("groupId") Long groupId);

    @Query("select m from AgentGroupMember m join fetch m.agent a where m.group.id = :groupId and m.agent.id = :agentId")
    Optional<AgentGroupMember> findMember(@Param("groupId") Long groupId, @Param("agentId") Long agentId);
}
