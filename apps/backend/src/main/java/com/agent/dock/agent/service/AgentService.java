package com.agent.dock.agent.service;

import com.agent.dock.agent.domain.Agent;
import com.agent.dock.agent.dto.AgentResponse;
import com.agent.dock.agent.dto.CreateAgentRequest;
import com.agent.dock.agent.dto.UpdateAgentRequest;
import com.agent.dock.agent.repository.AgentRepository;
import com.agent.dock.attachment.repository.AttachmentRepository;
import com.agent.dock.common.exception.ConflictException;
import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.execution.repository.ExecutionLogRepository;
import com.agent.dock.execution.repository.ExecutionRepository;
import com.agent.dock.group.domain.AgentGroup;
import com.agent.dock.group.domain.AgentGroupMember;
import com.agent.dock.group.repository.AgentGroupMemberRepository;
import com.agent.dock.group.repository.AgentGroupRepository;
import com.agent.dock.permission.repository.PermissionProfileRepository;
import com.agent.dock.project.domain.Project;
import com.agent.dock.project.domain.ProjectWorkspace;
import com.agent.dock.project.repository.ProjectRepository;
import com.agent.dock.project.repository.ProjectWorkspaceRepository;
import com.agent.dock.provider.domain.AiProvider;
import com.agent.dock.provider.domain.ConnectionStatus;
import com.agent.dock.provider.repository.AiProviderRepository;
import com.agent.dock.role.repository.AgentRoleRepository;
import com.agent.dock.task.repository.TaskRepository;
import com.agent.dock.workspace.domain.WorkspaceRuntimeStatus;
import com.agent.dock.workspace.repository.WorkspaceRuntimeStatusRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AgentGroupRepository groupRepository;
    private final AgentGroupMemberRepository memberRepository;
    private final TaskRepository taskRepository;
    private final ExecutionRepository executionRepository;
    private final ExecutionLogRepository executionLogRepository;
    private final AttachmentRepository attachmentRepository;

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

    /**
     * 에이전트 수정(이름·역할·권한·런타임·모델·모드·페르소나). 소속 프로젝트와 배치는 건드리지 않는다.
     * 런타임은 생성과 같은 규칙으로 검증한다(존재·논리 삭제 아님·켜짐).
     */
    @Transactional
    public AgentResponse update(Long id, UpdateAgentRequest request) {
        Agent agent = agentRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new NotFoundException("Agent %d not found".formatted(id)));
        AiProvider provider = findEnabledProvider(request.providerId());
        agent.setName(request.name());
        agent.setRole(roleRepository.findById(request.roleId())
                .orElseThrow(() -> new NotFoundException("AgentRole %d not found".formatted(request.roleId()))));
        agent.setPermissionProfile(permissionProfileRepository.findById(request.permissionProfileId())
                .orElseThrow(() -> new NotFoundException("PermissionProfile %d not found".formatted(request.permissionProfileId()))));
        agent.setProvider(provider);
        agent.setModel(request.model());
        agent.setMode(request.mode());
        agent.setPersona(request.persona());
        agentRepository.save(agent);
        return findOne(id);
    }

    /** 사용 불가가 된 에이전트에 다른 런타임을 다시 할당한다. 켜져 있는 런타임만 가능하다. */
    public AgentResponse assignProvider(Long agentId, Long providerId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new NotFoundException("Agent %d not found".formatted(agentId)));
        agent.setProvider(findEnabledProvider(providerId));
        agentRepository.save(agent);
        return findOne(agentId);
    }

    /**
     * 에이전트 삭제. 마스터는 지울 수 없다(목업 규칙, 409).
     *
     * <p>FK 안전 순서로 지운다: 그룹에서 제거(리더였으면 남은 첫 멤버가 이어받는다) → 담당 Task 와 그 실행·로그·첨부
     * → 이 에이전트가 낸 실행과 로그 → 다른 실행의 위임 대상 참조 해제 → 에이전트.
     */
    @Transactional
    public void delete(Long id) {
        Agent agent = agentRepository.findByIdWithRelations(id)
                .orElseThrow(() -> new NotFoundException("Agent %d not found".formatted(id)));
        Project project = agent.getProject();
        if (id.equals(project.getMasterAgentId())) {
            throw new ConflictException("마스터 에이전트는 삭제할 수 없습니다");
        }
        removeFromGroups(project.getId(), id);
        deleteAssignedTasks(id);
        deleteExecutions(id);
        executionRepository.clearDelegatedTarget(id);
        agentRepository.delete(agent);
    }

    /** 모든 그룹에서 뺀다. 리더였다면 남은 첫 멤버가 리더를 이어받는다(목업과 같은 규칙). */
    private void removeFromGroups(Long projectId, Long agentId) {
        for (AgentGroup group : groupRepository.findByProjectWithRelations(projectId)) {
            memberRepository.findMember(group.getId(), agentId).ifPresent(memberRepository::delete);
            if (agentId.equals(group.getLeaderAgentId())) {
                group.setLeaderAgent(firstRemainingMember(group.getId(), agentId));
                groupRepository.save(group);
            }
        }
    }

    /** 같은 그룹의 남은 멤버 중 첫 번째(이름순). 없으면 null(리더 없음). */
    private Agent firstRemainingMember(Long groupId, Long removedAgentId) {
        return memberRepository.findMembersWithAgent(groupId).stream()
                .map(AgentGroupMember::getAgent)
                .filter(member -> !member.getId().equals(removedAgentId))
                .findFirst()
                .orElse(null);
    }

    /** 이 에이전트가 담당한 Task 는 남길 수 없다(FK). 실행·로그·첨부와 함께 지운다. */
    private void deleteAssignedTasks(Long agentId) {
        List<Long> taskIds = taskRepository.findIdsByAgentId(agentId);
        if (taskIds.isEmpty()) {
            return;
        }
        deleteExecutionRows(executionRepository.findIdsByTaskIdIn(taskIds));
        attachmentRepository.deleteByTaskIdIn(taskIds);
        taskRepository.deleteAllByIdInBatch(taskIds);
    }

    /** 이 에이전트가 낸 실행을 로그와 함께 지운다. */
    private void deleteExecutions(Long agentId) {
        deleteExecutionRows(executionRepository.findIdsByAgentId(agentId));
    }

    /** 실행 로그를 먼저 지운 뒤 실행을 지운다(FK 안전). */
    private void deleteExecutionRows(List<Long> executionIds) {
        if (executionIds.isEmpty()) {
            return;
        }
        executionLogRepository.deleteByExecutionIdIn(executionIds);
        executionRepository.deleteAllByIdInBatch(executionIds);
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
