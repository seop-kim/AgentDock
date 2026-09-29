package com.agent.dock.agent;

import com.agent.dock.permission.PermissionProfileResponse;
import com.agent.dock.project.ProjectSummary;
import com.agent.dock.provider.AiConnectionResponse;
import com.agent.dock.provider.AiProviderSummary;
import com.agent.dock.role.AgentRoleResponse;

import java.time.Instant;
import java.util.Map;

public record AgentResponse(
        Long id, String name,
        Long projectId, ProjectSummary project,
        Long roleId, AgentRoleResponse role,
        Long permissionProfileId, PermissionProfileResponse permissionProfile,
        Long providerId, AiProviderSummary provider,
        Long connectionId, AiConnectionResponse connection,
        String persona, String model, String mode, Map<String, Object> profile,
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
                a.getConnectionId(), a.getConnection() == null ? null : AiConnectionResponse.from(a.getConnection()),
                a.getPersona(), a.getModel(), a.getMode(), a.getProfile(),
                availability.available(), availability.reason() == null ? null : availability.reason().name(),
                a.getCreatedAt(), a.getUpdatedAt()
        );
    }
}
