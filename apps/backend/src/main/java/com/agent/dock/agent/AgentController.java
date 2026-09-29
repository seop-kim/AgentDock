package com.agent.dock.agent;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/agents")
@RequiredArgsConstructor
public class AgentController {
    private final AgentService service;

    @GetMapping
    public List<AgentResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public AgentResponse findOne(@PathVariable Long id) {
        return service.findOne(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AgentResponse create(@Valid @RequestBody CreateAgentRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}/provider")
    public AgentResponse assignProvider(@PathVariable Long id, @Valid @RequestBody AssignProviderRequest request) {
        return service.assignProvider(id, request.providerId());
    }
}
