package com.agent.dock.group;

public record GroupSummary(Long id, String name) {
    public static GroupSummary from(AgentGroup g) {
        return new GroupSummary(g.getId(), g.getName());
    }
}
