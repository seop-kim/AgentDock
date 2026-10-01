package com.agent.dock.agent.dto;

import com.agent.dock.agent.domain.Agent;

public record AgentSummary(Long id, String name) {
    public static AgentSummary from(Agent agent) {
        return new AgentSummary(agent.getId(), agent.getName());
    }
}
