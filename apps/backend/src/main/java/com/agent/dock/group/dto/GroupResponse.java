package com.agent.dock.group.dto;

import com.agent.dock.agent.dto.AgentSummary;
import com.agent.dock.group.domain.AgentGroup;
import java.time.Instant;
import java.util.List;

public record GroupResponse(
        Long id, Long projectId, String name, String description, String prompt,
        // 그룹 프롬프트와 별개인 공유 노트(그룹 안 실행들이 함께 보는 맥락 — 화면에서 보고 고칠 수 있다).
        String sharedNote,
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
                g.getPrompt() == null ? "" : g.getPrompt(),
                g.getSharedNote() == null ? "" : g.getSharedNote(), leader, members,
                g.getNodeX(), g.getNodeY(), g.getCreatedAt(), g.getUpdatedAt());
    }
}
