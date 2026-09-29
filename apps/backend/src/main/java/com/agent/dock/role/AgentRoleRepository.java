package com.agent.dock.role;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentRoleRepository extends JpaRepository<AgentRole, Long> {
    List<AgentRole> findAllByOrderByNameAsc();
}
