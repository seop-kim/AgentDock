package com.agent.dock.execution;

import com.agent.dock.agent.Agent;
import com.agent.dock.agent.AgentAvailability;
import com.agent.dock.agent.AgentRepository;
import com.agent.dock.agent.PromptLayers;
import com.agent.dock.common.ConflictException;
import com.agent.dock.common.ForbiddenException;
import com.agent.dock.common.NotFoundException;
import com.agent.dock.group.AgentGroupRepository;
import com.agent.dock.permission.PermissionAction;
import com.agent.dock.permission.PermissionService;
import com.agent.dock.project.Project;
import com.agent.dock.project.ProjectRepository;
import com.agent.dock.project.ProjectService;
import com.agent.dock.provider.ConnectionStatus;
import com.agent.dock.workspace.Workspace;
import com.agent.dock.workspace.WorkspaceRuntimeStatus;
import com.agent.dock.workspace.WorkspaceRuntimeStatusRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 실행 전에 불변 규칙을 강제한다(권한·기본 워크스페이스·런타임 상태). 통과하면 실행에 필요한 값만 남긴다.
 * 단일 실행과 위임 실행(자식 포함)이 같은 가드를 지나야 하므로 따로 둔다.
 */
@Component
@RequiredArgsConstructor
public class ExecutionGuard {

    private final AgentRepository agentRepository;
    private final ProjectRepository projectRepository;
    private final ProjectService projectService;
    private final PermissionService permissionService;
    private final WorkspaceRuntimeStatusRepository runtimeStatusRepository;
    private final AgentGroupRepository groupRepository;

    /**
     * 가드를 통과한 실행 대상. 엔티티 대신 값만 들고 있어 다른 스레드로 넘겨도 안전하다.
     *
     * @param systemPrompt 프롬프트 계층(마스터 → 그룹 → 에이전트)을 합친 시스템 프롬프트
     */
    public record Target(Long agentId, Long workspaceId, String workspacePath, String systemPrompt) {
    }

    public Target prepare(Long agentId, Long projectId, Long groupId) {
        Agent agent = agentRepository.findByIdWithRelations(agentId)
                .orElseThrow(() -> new NotFoundException("Agent %d not found".formatted(agentId)));
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project %d not found".formatted(projectId)));

        // Permission Enforcement: Prompt 설명이 아니라 실행 전 Backend에서 실제로 차단한다.
        if (!permissionService.isAllowed(agent.getPermissionProfile(), PermissionAction.TERMINAL_EXECUTE)) {
            throw new ForbiddenException("Agent permission profile does not allow TERMINAL_EXECUTE");
        }

        // 작업 디렉터리는 프로젝트의 기본 워크스페이스다. 할당된 워크스페이스가 없으면 실행할 수 없다.
        Workspace workspace = projectService.defaultWorkspace(projectId);

        // 런타임이 꺼져 있거나 그 폴더에서 확인되지 않았으면 Runtime 을 호출하지 않고 즉시 거부한다(새 실행만 차단).
        ConnectionStatus workspaceStatus = runtimeStatusRepository
                .findByWorkspaceIdAndProviderId(workspace.getId(), agent.getProvider().getId())
                .map(WorkspaceRuntimeStatus::getStatus)
                .orElse(ConnectionStatus.DISCONNECTED);
        var availability = AgentAvailability.evaluate(
                agent.getProvider().getDeletedAt() != null, agent.getProvider().isEnabled(), workspaceStatus);
        if (!availability.available()) {
            throw new ConflictException(availability.message());
        }

        return new Target(agent.getId(), workspace.getId(), workspace.getPath(),
                systemPrompt(project, groupId, agent));
    }

    /** 프롬프트 계층: 마스터(프로젝트) → 그룹 → 에이전트. 그룹을 통해 실행될 때만 그룹 프롬프트가 끼어든다. */
    private String systemPrompt(Project project, Long groupId, Agent agent) {
        String groupPrompt = groupId == null ? "" : groupRepository.findById(groupId)
                .map(group -> group.getPrompt())
                .orElse("");
        return PromptLayers.combine(project.getMasterPrompt(), groupPrompt, agent.getPersona());
    }
}
