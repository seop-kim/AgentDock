package com.agent.dock.agent.dto;

import com.agent.dock.agent.domain.Agent;
import com.agent.dock.agent.service.AgentAvailability;
import com.agent.dock.permission.dto.PermissionProfileResponse;
import com.agent.dock.project.dto.ProjectSummary;
import com.agent.dock.provider.dto.AiProviderSummary;
import com.agent.dock.role.dto.AgentRoleResponse;
import java.time.Instant;
import java.util.Map;

public record AgentResponse(
        Long id, String name,
        Long projectId, ProjectSummary project,
        Long roleId, AgentRoleResponse role,
        Long permissionProfileId, PermissionProfileResponse permissionProfile,
        Long providerId, AiProviderSummary provider,
        String persona, String model, String mode, Map<String, Object> profile,
        boolean placed, Double nodeX, Double nodeY,
        boolean available, String unavailableReason,
        Instant createdAt, Instant updatedAt
) {
    public static AgentResponse from(Agent a, AgentAvailability.Result availability) {
        return new AgentResponse(
                a.getId(), a.getName(),
                a.getProjectId(), a.getProject() == null ? null : ProjectSummary.from(a.getProject()),
                a.getRoleId(), AgentRoleResponse.from(a.getRole()),
                a.getPermissionProfileId(), PermissionProfileResponse.from(a.getPermissionProfile()),
                a.getProviderId(), AiProviderSummary.from(a.getProvider()),
                a.getPersona(), a.getModel(), a.getMode(), a.getProfile(),
                a.isPlaced(), a.getNodeX(), a.getNodeY(),
                availability.available(), availability.reason() == null ? null : availability.reason().name(),
                a.getCreatedAt(), a.getUpdatedAt()
        );
    }
}
