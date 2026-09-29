package com.agent.dock.agent;

import com.agent.dock.permission.PermissionProfileResponse;
import com.agent.dock.provider.AiConnectionSummary;
import com.agent.dock.provider.AiProviderSummary;
import com.agent.dock.role.AgentRoleResponse;
import com.agent.dock.workspace.WorkspaceResponse;

import java.time.Instant;
import java.util.Map;

public record AgentResponse(
        Long id, String name,
        Long roleId, AgentRoleResponse role,
        Long permissionProfileId, PermissionProfileResponse permissionProfile,
        Long providerId, AiProviderSummary provider,
        Long connectionId, AiConnectionSummary connection,
        Long workspaceId, WorkspaceResponse workspace,
        String model, String mode, Map<String, Object> profile,
        Instant createdAt, Instant updatedAt
) {
    public static AgentResponse from(Agent a) {
        return new AgentResponse(
                a.getId(), a.getName(),
                a.getRoleId(), AgentRoleResponse.from(a.getRole()),
                a.getPermissionProfileId(), PermissionProfileResponse.from(a.getPermissionProfile()),
                a.getProviderId(), AiProviderSummary.from(a.getProvider()),
                a.getConnectionId(), a.getConnection() == null ? null : AiConnectionSummary.from(a.getConnection()),
                a.getWorkspaceId(), a.getWorkspace() == null ? null : WorkspaceResponse.from(a.getWorkspace()),
                a.getModel(), a.getMode(), a.getProfile(),
                a.getCreatedAt(), a.getUpdatedAt()
        );
    }
}
