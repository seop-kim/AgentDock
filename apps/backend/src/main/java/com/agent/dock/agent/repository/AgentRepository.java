package com.agent.dock.agent.repository;

import com.agent.dock.agent.domain.Agent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRepository extends JpaRepository<Agent, Long>, AgentRepositoryCustom {
}
