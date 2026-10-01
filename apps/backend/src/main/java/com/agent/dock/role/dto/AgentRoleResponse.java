package com.agent.dock.role.dto;

import com.agent.dock.role.domain.AgentRole;
import java.time.Instant;

public record AgentRoleResponse(Long id, String name, String description, Instant createdAt, Instant updatedAt) {
    public static AgentRoleResponse from(AgentRole r) {
        return new AgentRoleResponse(r.getId(), r.getName(), r.getDescription(), r.getCreatedAt(), r.getUpdatedAt());
    }
}
