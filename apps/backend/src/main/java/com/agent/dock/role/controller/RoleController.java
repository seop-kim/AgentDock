package com.agent.dock.role.controller;

import com.agent.dock.role.dto.AgentRoleResponse;
import com.agent.dock.role.dto.CreateAgentRoleRequest;
import com.agent.dock.role.service.RoleService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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
    @ResponseStatus(HttpStatus.CREATED)
    public AgentRoleResponse create(@Valid @RequestBody CreateAgentRoleRequest request) {
        return service.create(request);
    }
}
