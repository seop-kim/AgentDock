package com.agent.dock.agent;

import java.util.List;
import java.util.Optional;

public interface AgentRepositoryCustom {
    List<Agent> findAllWithRelations();
    Optional<Agent> findByIdWithRelations(Long id);
}
