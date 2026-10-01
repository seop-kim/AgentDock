package com.agent.dock.workspace.controller;

import com.agent.dock.workspace.domain.Workspace;
import com.agent.dock.workspace.dto.CreateWorkspaceRequest;
import com.agent.dock.workspace.dto.WorkspaceBrowseResult;
import com.agent.dock.workspace.dto.WorkspaceResponse;
import com.agent.dock.workspace.dto.WorkspaceRuntimeResponse;
import com.agent.dock.workspace.service.WorkspaceRuntimeService;
import com.agent.dock.workspace.service.WorkspaceService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {
    private final WorkspaceService service;
    private final WorkspaceRuntimeService runtimeService;

    @GetMapping
    public List<WorkspaceResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/browse")
    public WorkspaceBrowseResult browse(@RequestParam(required = false) String path) {
        return service.browse(path);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceResponse create(@Valid @RequestBody CreateWorkspaceRequest request) {
        return service.create(request);
    }

    /** 켜져 있는 런타임들과 이 폴더에서의 상태. */
    @GetMapping("/{id}/runtimes")
    public List<WorkspaceRuntimeResponse> listRuntimes(@PathVariable Long id) {
        return runtimeService.findAll(id);
    }

    /** 이 폴더를 작업 디렉터리로 CLI 를 실제 실행해 확인한다. */
    @PostMapping("/{id}/runtimes/{providerId}/check")
    @ResponseStatus(HttpStatus.CREATED)
    public WorkspaceRuntimeResponse checkRuntime(@PathVariable Long id, @PathVariable Long providerId) {
        return runtimeService.check(id, providerId);
    }
}
