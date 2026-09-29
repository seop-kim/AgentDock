package com.agent.dock.agent;

public record AgentSummary(Long id, String name) {
    public static AgentSummary from(Agent agent) {
        return new AgentSummary(agent.getId(), agent.getName());
    }
}
