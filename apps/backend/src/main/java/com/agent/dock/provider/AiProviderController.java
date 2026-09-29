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

    /** 등록된 런타임 목록. 연결 상태는 워크스페이스별(`/workspaces/{id}/runtimes`)이다. */
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

    /** 런타임 on/off 토글. */
    @PutMapping("/{id}/enabled")
    public AiProviderResponse setEnabled(@PathVariable Long id, @Valid @RequestBody RuntimeEnabledRequest request) {
        return service.setEnabled(id, request.enabled());
    }

    @PutMapping("/{id}/capabilities")
    public AiProviderResponse updateCapabilities(@PathVariable Long id, @Valid @RequestBody ProviderCapabilities request) {
        return service.updateCapabilities(id, request);
    }
}
