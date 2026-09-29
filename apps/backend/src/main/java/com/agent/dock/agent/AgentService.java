package com.agent.dock.agent;

import com.agent.dock.common.NotFoundException;
import com.agent.dock.permission.PermissionProfileRepository;
import com.agent.dock.provider.AiConnectionRepository;
import com.agent.dock.provider.AiProviderRepository;
import com.agent.dock.role.AgentRoleRepository;
import com.agent.dock.workspace.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AgentService {
    private final AgentRepository agentRepository;
    private final AgentRoleRepository roleRepository;
    private final PermissionProfileRepository permissionProfileRepository;
    private final AiProviderRepository providerRepository;
    private final AiConnectionRepository connectionRepository;
    private final WorkspaceRepository workspaceRepository;

    public List<AgentResponse> findAll() {
        return agentRepository.findAllWithRelations().stream().map(AgentResponse::from).toList();
    }

    public AgentResponse findOne(Long id) {
        Agent agent = agentRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new NotFoundException("Agent %d not found".formatted(id)));
        return AgentResponse.from(agent);
    }

    public AgentResponse create(CreateAgentRequest request) {
        Agent agent = new Agent();
        agent.setName(request.name());
        agent.setRole(roleRepository.findById(request.roleId())
                .orElseThrow(() -> new NotFoundException("AgentRole %d not found".formatted(request.roleId()))));
        agent.setPermissionProfile(permissionProfileRepository.findById(request.permissionProfileId())
                .orElseThrow(() -> new NotFoundException("PermissionProfile %d not found".formatted(request.permissionProfileId()))));
        agent.setProvider(providerRepository.findById(request.providerId())
                .orElseThrow(() -> new NotFoundException("AiProvider %d not found".formatted(request.providerId()))));
        if (request.connectionId() != null) {
            agent.setConnection(connectionRepository.findById(request.connectionId())
                    .orElseThrow(() -> new NotFoundException("AiConnection %d not found".formatted(request.connectionId()))));
        }
        if (request.workspaceId() != null) {
            agent.setWorkspace(workspaceRepository.findById(request.workspaceId())
                    .orElseThrow(() -> new NotFoundException("Workspace %d not found".formatted(request.workspaceId()))));
        }
        agent.setModel(request.model());
        agent.setMode(request.mode());
        agent.setProfile(request.profile());
        Agent saved = agentRepository.save(agent);
        return findOne(saved.getId());
    }
}
