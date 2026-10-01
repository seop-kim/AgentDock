package com.agent.dock.permission.controller;

import com.agent.dock.permission.dto.CreatePermissionProfileRequest;
import com.agent.dock.permission.dto.PermissionProfileResponse;
import com.agent.dock.permission.service.PermissionService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/permission-profiles")
@RequiredArgsConstructor
public class PermissionController {
    private final PermissionService service;

    @GetMapping
    public List<PermissionProfileResponse> findAll() {
        return service.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PermissionProfileResponse create(@Valid @RequestBody CreatePermissionProfileRequest request) {
        return service.create(request);
    }
}
