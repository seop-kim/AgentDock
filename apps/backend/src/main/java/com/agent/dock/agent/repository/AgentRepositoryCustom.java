package com.agent.dock.agent.repository;

import com.agent.dock.agent.domain.Agent;
import java.util.List;
import java.util.Optional;

public interface AgentRepositoryCustom {
    List<Agent> findAllWithRelations();
    Optional<Agent> findByIdWithRelations(Long id);
}
