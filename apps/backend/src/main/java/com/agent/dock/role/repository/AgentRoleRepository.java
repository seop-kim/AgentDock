package com.agent.dock.role.repository;

import com.agent.dock.role.domain.AgentRole;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRoleRepository extends JpaRepository<AgentRole, Long> {
    List<AgentRole> findAllByOrderByNameAsc();
}
