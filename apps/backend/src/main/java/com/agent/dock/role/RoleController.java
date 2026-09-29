package com.agent.dock.role;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {
    private final RoleService service;

    @GetMapping
    public List<AgentRoleResponse> findAll() {
        return service.findAll();
    }

    @PostMapping
    public AgentRoleResponse create(@Valid @RequestBody CreateAgentRoleRequest request) {
        return service.create(request);
    }
}
