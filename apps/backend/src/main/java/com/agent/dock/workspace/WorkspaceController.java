package com.agent.dock.workspace;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/workspaces")
@RequiredArgsConstructor
public class WorkspaceController {
    private final WorkspaceService service;

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
}
