package com.agent.dock.project.service;

import com.agent.dock.agent.domain.Agent;
import com.agent.dock.agent.repository.AgentRepository;
import com.agent.dock.common.exception.BadRequestException;
import com.agent.dock.common.exception.ConflictException;
import com.agent.dock.common.exception.NotFoundException;
import com.agent.dock.project.domain.Project;
import com.agent.dock.project.domain.ProjectWorkspace;
import com.agent.dock.project.dto.CreateProjectRequest;
import com.agent.dock.project.dto.ProjectResponse;
import com.agent.dock.project.dto.UpdateMasterRequest;
import com.agent.dock.project.repository.ProjectRepository;
import com.agent.dock.project.repository.ProjectWorkspaceRepository;
import com.agent.dock.workspace.domain.Workspace;
import com.agent.dock.workspace.repository.WorkspaceRepository;
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
        return findOne(saved.getId());
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
