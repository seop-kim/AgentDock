package com.agent.dock.project.service;

import com.agent.dock.agent.domain.Agent;
import com.agent.dock.agent.repository.AgentRepository;
import com.agent.dock.attachment.repository.AttachmentRepository;
import com.agent.dock.common.exception.BadRequestException;
import com.agent.dock.common.exception.ConflictException;
import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.event.service.EventPublisher;
import com.agent.dock.execution.repository.ExecutionLogRepository;
import com.agent.dock.execution.repository.ExecutionRepository;
import com.agent.dock.group.domain.AgentGroup;
import com.agent.dock.group.repository.AgentGroupMemberRepository;
import com.agent.dock.group.repository.AgentGroupRepository;
import com.agent.dock.project.domain.Project;
import com.agent.dock.project.domain.ProjectWorkspace;
import com.agent.dock.project.dto.CreateProjectRequest;
import com.agent.dock.project.dto.ProjectLayoutRequest;
import com.agent.dock.project.dto.ProjectResponse;
import com.agent.dock.project.dto.UpdateMasterRequest;
import com.agent.dock.project.dto.UpdateProjectRequest;
import com.agent.dock.project.repository.ProjectRepository;
import com.agent.dock.project.repository.ProjectWorkspaceRepository;
import com.agent.dock.task.repository.TaskRepository;
import com.agent.dock.workspace.domain.Workspace;
import com.agent.dock.workspace.repository.WorkspaceRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository repository;
    private final WorkspaceRepository workspaceRepository;
    private final ProjectWorkspaceRepository workspaceLinkRepository;
    private final AgentRepository agentRepository;
    private final AgentGroupRepository groupRepository;
    private final AgentGroupMemberRepository memberRepository;
    private final TaskRepository taskRepository;
    private final ExecutionRepository executionRepository;
    private final ExecutionLogRepository executionLogRepository;
    private final AttachmentRepository attachmentRepository;
    /** 전역 SSE 스트림에 "프로젝트가 바뀌었다"를 알린다(구성도 배치 포함). */
    private final EventPublisher changeEvents;

    public List<ProjectResponse> findAll() {
        List<Project> projects = repository.findAllByOrderByNameAsc();
        Map<Long, List<ProjectWorkspace>> links = linksByProject(projects.stream().map(Project::getId).toList());
        return projects.stream()
                .map(project -> ProjectResponse.from(project, links.getOrDefault(project.getId(), List.of())))
                .toList();
    }

    public ProjectResponse findOne(Long id) {
        Project project = findProject(id);
        return ProjectResponse.from(project, workspaceLinkRepository.findByProjectIdOrderByIdAsc(id));
    }

    @Transactional
    public ProjectResponse create(CreateProjectRequest request) {
        if (repository.existsByName(request.name())) {
            throw new ConflictException("Project name already exists");
        }
        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());
        Project saved = repository.save(project);
        if (request.workspaceId() != null) {
            assignWorkspace(saved.getId(), request.workspaceId(), true);
        }
        changeEvents.projectChanged(saved.getId());
        return findOne(saved.getId());
    }

    /** 이름·설명 변경. 이름이 겹치면 409, 설명을 주지 않으면(null) 기존 설명을 유지한다. */
    @Transactional
    public ProjectResponse update(Long id, UpdateProjectRequest request) {
        Project project = findProject(id);
        if (!project.getName().equals(request.name()) && repository.existsByName(request.name())) {
            throw new ConflictException("Project name already exists");
        }
        project.setName(request.name());
        if (request.description() != null) {
            project.setDescription(request.description());
        }
        repository.save(project);
        changeEvents.projectChanged(id);
        return findOne(id);
    }

    /**
     * 프로젝트 삭제. FK 안전 순서로 지운다:
     * 마스터 참조 해제 → 실행 로그 → 실행(프로젝트 Task 또는 프로젝트 에이전트의 것) → 프로젝트 Task 의 첨부
     * → Task → 그룹 멤버 → 그룹 → 에이전트 → 워크스페이스 할당 → 프로젝트.
     */
    @Transactional
    public void delete(Long id) {
        Project project = findProject(id);
        // project.master_agent_id → agent FK. 에이전트를 지우기 전에 참조를 끊는다.
        if (project.getMasterAgentId() != null) {
            project.setMasterAgent(null);
            repository.saveAndFlush(project);
        }
        List<Long> agentIds = agentRepository.findByProjectId(id).stream().map(Agent::getId).toList();
        List<Long> taskIds = taskRepository.findIdsByProjectId(id);
        List<Long> groupIds = groupRepository.findByProjectId(id).stream().map(AgentGroup::getId).toList();

        List<Long> executionIds = new ArrayList<>();
        if (!taskIds.isEmpty()) {
            executionIds.addAll(executionRepository.findIdsByTaskIdIn(taskIds));
        }
        if (!agentIds.isEmpty()) {
            executionIds.addAll(executionRepository.findIdsByAgentIdIn(agentIds));
        }
        List<Long> uniqueExecutionIds = executionIds.stream().distinct().toList();
        if (!uniqueExecutionIds.isEmpty()) {
            executionLogRepository.deleteByExecutionIdIn(uniqueExecutionIds);
            executionRepository.deleteAllByIdInBatch(uniqueExecutionIds);
        }
        if (!taskIds.isEmpty()) {
            attachmentRepository.deleteByTaskIdIn(taskIds);
            taskRepository.deleteAllByIdInBatch(taskIds);
        }
        if (!groupIds.isEmpty()) {
            memberRepository.deleteByGroupIdIn(groupIds);
            groupRepository.deleteAllByIdInBatch(groupIds);
        }
        if (!agentIds.isEmpty()) {
            agentRepository.deleteAllByIdInBatch(agentIds);
        }
        workspaceLinkRepository.deleteByProjectId(id);
        repository.delete(project);
        changeEvents.projectChanged(id);
    }

    /** 구성도 배치 저장(캔버스 드래그 결과). 프로젝트에 속하지 않는 에이전트/그룹이면 400. */
    @Transactional
    public void saveLayout(Long projectId, ProjectLayoutRequest request) {
        findProject(projectId);
        if (request.agents() != null) {
            for (ProjectLayoutRequest.AgentPlacement placement : request.agents()) {
                Agent agent = requireProjectAgent(projectId, placement.agentId());
                agent.setNodeX(placement.x());
                agent.setNodeY(placement.y());
                if (placement.placed() != null) {
                    agent.setPlaced(placement.placed());
                }
                agentRepository.save(agent);
            }
        }
        if (request.groups() != null) {
            for (ProjectLayoutRequest.GroupPlacement placement : request.groups()) {
                AgentGroup group = requireProjectGroup(projectId, placement.groupId());
                group.setNodeX(placement.x());
                group.setNodeY(placement.y());
                groupRepository.save(group);
            }
        }
        changeEvents.projectChanged(projectId);
    }

    /** 손으로 옮긴 좌표를 모두 비우고(자동 배치로 되돌림) 에이전트를 다시 놓는다. */
    @Transactional
    public void clearLayout(Long projectId) {
        findProject(projectId);
        for (Agent agent : agentRepository.findByProjectId(projectId)) {
            agent.setNodeX(null);
            agent.setNodeY(null);
            agent.setPlaced(true);
            agentRepository.save(agent);
        }
        for (AgentGroup group : groupRepository.findByProjectId(projectId)) {
            group.setNodeX(null);
            group.setNodeY(null);
            groupRepository.save(group);
        }
        changeEvents.projectChanged(projectId);
    }

    private Agent requireProjectAgent(Long projectId, Long agentId) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new BadRequestException(
                        "Agent %d does not belong to project %d".formatted(agentId, projectId)));
        if (agent.getProject() == null || !agent.getProject().getId().equals(projectId)) {
            throw new BadRequestException("Agent %d does not belong to project %d".formatted(agentId, projectId));
        }
        return agent;
    }

    private AgentGroup requireProjectGroup(Long projectId, Long groupId) {
        AgentGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> new BadRequestException(
                        "Group %d does not belong to project %d".formatted(groupId, projectId)));
        if (group.getProject() == null || !group.getProject().getId().equals(projectId)) {
            throw new BadRequestException("Group %d does not belong to project %d".formatted(groupId, projectId));
        }
        return group;
    }

    /** 워크스페이스를 할당한다. 첫 할당이거나 isDefault 면 기본이 되고, 기존 기본은 해제한다. */
    @Transactional
    public ProjectResponse assignWorkspace(Long projectId, Long workspaceId, boolean isDefault) {
        findProject(projectId);
        if (workspaceLinkRepository.existsByProjectIdAndWorkspaceId(projectId, workspaceId)) {
            throw new ConflictException("Workspace %d is already assigned to project %d".formatted(workspaceId, projectId));
        }
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new NotFoundException("Workspace %d not found".formatted(workspaceId)));
        boolean firstAssignment = workspaceLinkRepository.countByProjectId(projectId) == 0;
        boolean makeDefault = firstAssignment || isDefault;

        ProjectWorkspace link = new ProjectWorkspace();
        link.setProject(repository.getReferenceById(projectId));
        link.setWorkspace(workspace);
        // 기본은 프로젝트당 하나만 허용된다(부분 유니크 인덱스).
        // 새 기본을 넣을 때 기존 기본을 먼저 내려야 인덱스를 잠깐이라도 위반하지 않는다.
        link.setDefault(false);
        ProjectWorkspace saved = workspaceLinkRepository.saveAndFlush(link);
        if (makeDefault) {
            clearOtherDefaults(projectId, saved.getId());
            workspaceLinkRepository.flush();
            saved.setDefault(true);
            workspaceLinkRepository.save(saved);
        }
        changeEvents.projectChanged(projectId);
        return findOne(projectId);
    }

    /** 할당을 해제한다. 기본 워크스페이스를 해제하면 남은 것 중 첫 번째가 기본이 된다. */
    @Transactional
    public void removeWorkspace(Long projectId, Long workspaceId) {
        ProjectWorkspace link = workspaceLinkRepository.findByProjectIdOrderByIdAsc(projectId).stream()
                .filter(candidate -> candidate.getWorkspace().getId().equals(workspaceId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException(
                        "Workspace %d is not assigned to project %d".formatted(workspaceId, projectId)));
        boolean wasDefault = link.isDefault();
        workspaceLinkRepository.delete(link);
        if (wasDefault) {
            workspaceLinkRepository.findByProjectIdOrderByIdAsc(projectId).stream().findFirst().ifPresent(next -> {
                next.setDefault(true);
                workspaceLinkRepository.save(next);
            });
        }
        changeEvents.projectChanged(projectId);
    }

    /** 실행에 쓸 기본 워크스페이스. 할당된 워크스페이스가 없으면 실행할 수 없다(409). */
    public Workspace defaultWorkspace(Long projectId) {
        return workspaceLinkRepository.findFirstByProjectIdAndIsDefaultTrue(projectId)
                .or(() -> workspaceLinkRepository.findFirstByProjectIdOrderByIdAsc(projectId))
                .map(ProjectWorkspace::getWorkspace)
                .orElseThrow(() -> new ConflictException("Project %d has no workspace assigned".formatted(projectId)));
    }

    /** 마스터 에이전트 지정/변경과 마스터 프롬프트(프롬프트 계층 맨 위). agentId 가 null 이면 해제한다. */
    @Transactional
    public ProjectResponse updateMaster(Long projectId, UpdateMasterRequest request) {
        Project project = findProject(projectId);
        if (request.agentId() == null) {
            project.setMasterAgent(null);
        } else {
            Agent agent = agentRepository.findById(request.agentId())
                    .orElseThrow(() -> new NotFoundException("Agent %d not found".formatted(request.agentId())));
            if (agent.getProject() == null || !agent.getProject().getId().equals(projectId)) {
                throw new BadRequestException("Agent does not belong to project");
            }
            project.setMasterAgent(agent);
        }
        project.setMasterPrompt(request.masterPrompt() == null ? "" : request.masterPrompt());
        repository.save(project);
        changeEvents.projectChanged(projectId);
        return findOne(projectId);
    }

    private void clearOtherDefaults(Long projectId, Long keepLinkId) {
        workspaceLinkRepository.findByProjectIdOrderByIdAsc(projectId).stream()
                .filter(candidate -> !candidate.getId().equals(keepLinkId) && candidate.isDefault())
                .forEach(candidate -> {
                    candidate.setDefault(false);
                    workspaceLinkRepository.save(candidate);
                });
    }

    private Map<Long, List<ProjectWorkspace>> linksByProject(List<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        return workspaceLinkRepository.findByProjectIdIn(projectIds).stream()
                .collect(Collectors.groupingBy(link -> link.getProject().getId()));
    }

    private Project findProject(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Project %d not found".formatted(id)));
    }
}
