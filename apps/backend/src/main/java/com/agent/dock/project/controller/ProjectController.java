package com.agent.dock.project.controller;

import com.agent.dock.project.domain.Project;
import com.agent.dock.project.dto.AssignWorkspaceRequest;
import com.agent.dock.project.dto.CreateProjectRequest;
import com.agent.dock.project.dto.ProjectResponse;
import com.agent.dock.project.dto.UpdateMasterRequest;
import com.agent.dock.project.service.ProjectService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {
    private final ProjectService service;

    @GetMapping
    public List<ProjectResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ProjectResponse findOne(@PathVariable Long id) {
        return service.findOne(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(@Valid @RequestBody CreateProjectRequest request) {
        return service.create(request);
    }

    /** 프로젝트에 워크스페이스 할당(여러 개 가능, 기본 1개). */
    @PostMapping("/{id}/workspaces")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse assignWorkspace(@PathVariable Long id, @Valid @RequestBody AssignWorkspaceRequest request) {
        return service.assignWorkspace(id, request.workspaceId(), Boolean.TRUE.equals(request.isDefault()));
    }

    @DeleteMapping("/{id}/workspaces/{workspaceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeWorkspace(@PathVariable Long id, @PathVariable Long workspaceId) {
        service.removeWorkspace(id, workspaceId);
    }

    /** 마스터 에이전트 지정/변경과 마스터 프롬프트. */
    @PutMapping("/{id}/master")
    public ProjectResponse updateMaster(@PathVariable Long id, @RequestBody UpdateMasterRequest request) {
        return service.updateMaster(id, request);
    }
}
