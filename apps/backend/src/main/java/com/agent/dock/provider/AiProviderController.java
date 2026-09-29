package com.agent.dock.provider;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ai-providers")
@RequiredArgsConstructor
public class AiProviderController {
    private final AiProviderService service;

    @GetMapping
    public List<AiProviderResponse> findAll() {
        return service.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AiProviderResponse create(@Valid @RequestBody CreateAiProviderRequest request) {
        return service.create(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping("/connections")
    @ResponseStatus(HttpStatus.CREATED)
    public AiConnectionResponse createConnection(@Valid @RequestBody CreateAiConnectionRequest request) {
        return service.createConnection(request);
    }

    @PostMapping("/{id}/connection")
    @ResponseStatus(HttpStatus.CREATED)
    public AiConnectionResponse createProviderConnection(@PathVariable Long id) {
        return service.createProviderConnection(id);
    }

    @GetMapping("/{id}/connections")
    public List<AiConnectionResponse> listConnections(@PathVariable Long id) {
        return service.listConnections(id);
    }
}
