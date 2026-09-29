package com.agent.dock.permission;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public PermissionProfileResponse create(@Valid @RequestBody CreatePermissionProfileRequest request) {
        return service.create(request);
    }
}
