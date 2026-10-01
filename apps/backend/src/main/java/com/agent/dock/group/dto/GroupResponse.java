package com.agent.dock.group.dto;

import com.agent.dock.agent.dto.AgentSummary;
import com.agent.dock.group.domain.AgentGroup;
import java.time.Instant;
import java.util.List;

public record GroupResponse(
        Long id, Long projectId, String name, String description, String prompt,
        AgentSummary leader, List<AgentSummary> members,
        Double nodeX, Double nodeY,
        Instant createdAt, Instant updatedAt
) {
    public static GroupResponse from(AgentGroup g, List<AgentSummary> members) {
        // 저장 직후에는 insertable=false 인 shadow FK 컬럼이 아직 채워지지 않으므로 관계에서 보완한다.
        Long projectId = g.getProjectId() != null ? g.getProjectId()
                : (g.getProject() != null ? g.getProject().getId() : null);
        AgentSummary leader = g.getLeaderAgent() == null ? null : AgentSummary.from(g.getLeaderAgent());
        return new GroupResponse(g.getId(), projectId, g.getName(), g.getDescription(),
                g.getPrompt() == null ? "" : g.getPrompt(), leader, members,
                g.getNodeX(), g.getNodeY(), g.getCreatedAt(), g.getUpdatedAt());
    }
}
