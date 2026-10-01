package com.agent.dock.group.repository;

import com.agent.dock.group.domain.AgentGroupMember;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentGroupMemberRepository extends JpaRepository<AgentGroupMember, Long> {

    @Query("select m from AgentGroupMember m join fetch m.agent a where m.group.id = :groupId order by a.name asc")
    List<AgentGroupMember> findMembersWithAgent(@Param("groupId") Long groupId);

    @Query("select m from AgentGroupMember m join fetch m.agent a where m.group.id = :groupId and m.agent.id = :agentId")
    Optional<AgentGroupMember> findMember(@Param("groupId") Long groupId, @Param("agentId") Long agentId);

    /** 그룹을 지우기 전에 멤버 행을 먼저 지운다(그룹·프로젝트 삭제용, FK 안전). */
    @Modifying
    @Query("delete from AgentGroupMember m where m.group.id in :groupIds")
    void deleteByGroupIdIn(@Param("groupIds") Collection<Long> groupIds);
}
