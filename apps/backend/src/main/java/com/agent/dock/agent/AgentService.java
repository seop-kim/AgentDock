package com.agent.dock.agent;

import com.agent.dock.common.ConflictException;
import com.agent.dock.common.NotFoundException;
import com.agent.dock.permission.PermissionProfileRepository;
import com.agent.dock.project.ProjectWorkspace;
import com.agent.dock.project.ProjectWorkspaceRepository;
import com.agent.dock.project.ProjectRepository;
import com.agent.dock.provider.AiProvider;
import com.agent.dock.provider.AiProviderRepository;
import com.agent.dock.provider.ConnectionStatus;
import com.agent.dock.role.AgentRoleRepository;
import com.agent.dock.workspace.WorkspaceRuntimeStatus;
import com.agent.dock.workspace.WorkspaceRuntimeStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
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
    private final ProjectRepository projectRepository;
    private final ProjectWorkspaceRepository projectWorkspaceRepository;
    private final WorkspaceRuntimeStatusRepository runtimeStatusRepository;

    public List<AgentResponse> findAll() {
        List<Agent> agents = agentRepository.findAllWithRelations();
        Set<Long> projectIds = agents.stream().map(agent -> agent.getProject().getId()).collect(Collectors.toSet());
        Map<Long, Long> defaultWorkspaceByProject = defaultWorkspaceIds(projectIds);
        Map<String, WorkspaceRuntimeStatus> statuses = runtimeStatuses(defaultWorkspaceByProject.values());
        return agents.stream()
                .map(agent -> toResponse(agent, defaultWorkspaceByProject, statuses))
                .toList();
    }

    public AgentResponse findOne(Long id) {
        Agent agent = agentRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new NotFoundException("Agent %d not found".formatted(id)));
        Map<Long, Long> defaultWorkspaceByProject = defaultWorkspaceIds(Set.of(agent.getProject().getId()));
        Map<String, WorkspaceRuntimeStatus> statuses = runtimeStatuses(defaultWorkspaceByProject.values());
        return toResponse(agent, defaultWorkspaceByProject, statuses);
    }

    public AgentResponse create(CreateAgentRequest request) {
        AiProvider provider = findEnabledProvider(request.providerId());
        Agent agent = new Agent();
        agent.setName(request.name());
        agent.setProject(projectRepository.findById(request.projectId())
                .orElseThrow(() -> new NotFoundException("Project %d not found".formatted(request.projectId()))));
        agent.setRole(roleRepository.findById(request.roleId())
                .orElseThrow(() -> new NotFoundException("AgentRole %d not found".formatted(request.roleId()))));
        agent.setPermissionProfile(permissionProfileRepository.findById(request.permissionProfileId())
                .orElseThrow(() -> new NotFoundException("PermissionProfile %d not found".formatted(request.permissionProfileId()))));
        agent.setProvider(provider);
        agent.setPersona(request.persona());
        agent.setModel(request.model());
        agent.setMode(request.mode());
        agent.setProfile(request.profile());
        Agent saved = agentRepository.save(agent);
        return findOne(saved.getId());
    }

    /** 사용 불가가 된 에이전트에 다른 런타임을 다시 할당한다. 켜져 있는 런타임만 가능하다. */
    public AgentResponse assignProvider(Long agentId, Long providerId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new NotFoundException("Agent %d not found".formatted(agentId)));
        agent.setProvider(findEnabledProvider(providerId));
        agentRepository.save(agent);
        return findOne(agentId);
    }

    private AiProvider findEnabledProvider(Long providerId) {
        AiProvider provider = providerRepository.findByIdAndDeletedAtIsNull(providerId)
                .orElseThrow(() -> new NotFoundException("AiProvider %d not found".formatted(providerId)));
        if (!provider.isEnabled()) {
            throw new ConflictException("Runtime %s is turned off; enable it first".formatted(provider.getKey()));
        }
        return provider;
    }

    private AgentResponse toResponse(Agent agent, Map<Long, Long> defaultWorkspaceByProject,
                                     Map<String, WorkspaceRuntimeStatus> statuses) {
        AiProvider provider = agent.getProvider();
        Long workspaceId = defaultWorkspaceByProject.get(agent.getProject().getId());
        WorkspaceRuntimeStatus status = workspaceId == null ? null : statuses.get(statusKey(workspaceId, provider.getId()));
        ConnectionStatus workspaceStatus = status == null ? ConnectionStatus.DISCONNECTED : status.getStatus();
        var availability = AgentAvailability.evaluate(
                provider.getDeletedAt() != null, provider.isEnabled(), workspaceStatus);
        return AgentResponse.from(agent, availability);
    }

    /** 프로젝트 → 기본 워크스페이스. 기본 플래그가 없으면 먼저 할당된 것을 쓴다. */
    private Map<Long, Long> defaultWorkspaceIds(Set<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Long> result = new HashMap<>();
        for (ProjectWorkspace link : projectWorkspaceRepository.findByProjectIdIn(projectIds)) {
            Long projectId = link.getProject().getId();
            if (!result.containsKey(projectId) || link.isDefault()) {
                result.put(projectId, link.getWorkspace().getId());
            }
        }
        return result;
    }

    private Map<String, WorkspaceRuntimeStatus> runtimeStatuses(Collection<Long> workspaceIds) {
        if (workspaceIds.isEmpty()) {
            return Map.of();
        }
        return runtimeStatusRepository.findByWorkspaceIdIn(workspaceIds).stream()
                .collect(Collectors.toMap(
                        status -> statusKey(status.getWorkspace().getId(), status.getProvider().getId()),
                        status -> status));
    }

    private String statusKey(Long workspaceId, Long providerId) {
        return workspaceId + ":" + providerId;
    }
}
