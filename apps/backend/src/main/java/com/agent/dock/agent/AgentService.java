package com.agent.dock.agent;

import com.agent.dock.common.NotFoundException;
import com.agent.dock.permission.PermissionProfileRepository;
import com.agent.dock.provider.AiConnection;
import com.agent.dock.provider.AiConnectionRepository;
import com.agent.dock.provider.AiProvider;
import com.agent.dock.provider.AiProviderRepository;
import com.agent.dock.role.AgentRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AgentService {
    private final AgentRepository agentRepository;
    private final AgentRoleRepository roleRepository;
    private final PermissionProfileRepository permissionProfileRepository;
    private final AiProviderRepository providerRepository;
    private final AiConnectionRepository connectionRepository;

    public List<AgentResponse> findAll() {
        List<Agent> agents = agentRepository.findAllWithRelations();
        Set<Long> providerIds = agents.stream().map(a -> a.getProvider().getId()).collect(Collectors.toSet());
        Map<Long, List<AiConnection>> connectionsByProvider = providerIds.isEmpty() ? Map.of()
                : connectionRepository.findByProviderIdIn(providerIds).stream()
                        .collect(Collectors.groupingBy(AiConnection::getProviderId));
        return agents.stream()
                .map(a -> toResponse(a, connectionsByProvider.getOrDefault(a.getProvider().getId(), List.of())))
                .toList();
    }

    public AgentResponse findOne(Long id) {
        Agent agent = agentRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new NotFoundException("Agent %d not found".formatted(id)));
        return toResponse(agent, connectionRepository.findByProviderId(agent.getProvider().getId()));
    }

    public AgentResponse create(CreateAgentRequest request) {
        Agent agent = new Agent();
        agent.setName(request.name());
        agent.setRole(roleRepository.findById(request.roleId())
                .orElseThrow(() -> new NotFoundException("AgentRole %d not found".formatted(request.roleId()))));
        agent.setPermissionProfile(permissionProfileRepository.findById(request.permissionProfileId())
                .orElseThrow(() -> new NotFoundException("PermissionProfile %d not found".formatted(request.permissionProfileId()))));
        agent.setProvider(providerRepository.findByIdAndDeletedAtIsNull(request.providerId())
                .orElseThrow(() -> new NotFoundException("AiProvider %d not found".formatted(request.providerId()))));
        if (request.connectionId() != null) {
            agent.setConnection(connectionRepository.findById(request.connectionId())
                    .orElseThrow(() -> new NotFoundException("AiConnection %d not found".formatted(request.connectionId()))));
        }
        agent.setModel(request.model());
        agent.setMode(request.mode());
        agent.setProfile(request.profile());
        Agent saved = agentRepository.save(agent);
        return findOne(saved.getId());
    }

    /** 사용 불가가 된 Agent 에 다른 Provider 를 다시 할당한다. 삭제되지 않은 Provider 만 가능하다. */
    public AgentResponse assignProvider(Long agentId, Long providerId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new NotFoundException("Agent %d not found".formatted(agentId)));
        AiProvider provider = providerRepository.findByIdAndDeletedAtIsNull(providerId)
                .orElseThrow(() -> new NotFoundException("AiProvider %d not found".formatted(providerId)));
        agent.setProvider(provider);
        agent.setConnection(null);
        agentRepository.save(agent);
        return findOne(agentId);
    }

    private AgentResponse toResponse(Agent agent, List<AiConnection> providerConnections) {
        return AgentResponse.from(agent, AgentAvailability.evaluate(agent.getProvider(), providerConnections));
    }
}
