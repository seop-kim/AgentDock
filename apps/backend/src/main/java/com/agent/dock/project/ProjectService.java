package com.agent.dock.project;

import com.agent.dock.common.ConflictException;
import com.agent.dock.common.NotFoundException;
import com.agent.dock.workspace.Workspace;
import com.agent.dock.workspace.WorkspaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository repository;
    private final WorkspaceRepository workspaceRepository;

    public List<ProjectResponse> findAll() {
        return repository.findAllWithWorkspace().stream().map(ProjectResponse::from).toList();
    }

    public ProjectResponse findOne(Long id) {
        Project project = repository.findWithWorkspace(id)
                .orElseThrow(() -> new NotFoundException("Project %d not found".formatted(id)));
        return ProjectResponse.from(project);
    }

    public ProjectResponse create(CreateProjectRequest request) {
        if (repository.existsByName(request.name())) {
            throw new ConflictException("Project name already exists");
        }
        Workspace workspace = workspaceRepository.findById(request.workspaceId())
                .orElseThrow(() -> new NotFoundException("Workspace %d not found".formatted(request.workspaceId())));
        Project project = new Project();
        project.setName(request.name());
        project.setDescription(request.description());
        project.setWorkspace(workspace);
        Project saved = repository.save(project);
        return findOne(saved.getId());
    }
}
