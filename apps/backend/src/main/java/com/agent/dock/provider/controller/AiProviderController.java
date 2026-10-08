package com.agent.dock.provider.controller;

import com.agent.dock.provider.dto.AiProviderResponse;
import com.agent.dock.provider.dto.CliStatusResponse;
import com.agent.dock.provider.dto.CreateAiProviderRequest;
import com.agent.dock.provider.dto.ProviderCapabilities;
import com.agent.dock.provider.dto.RuntimeEnabledRequest;
import com.agent.dock.provider.dto.ProviderCatalogEntry;
import com.agent.dock.provider.dto.ProviderCliStatusResponse;
import com.agent.dock.provider.dto.UpdateAiProviderRequest;
import com.agent.dock.provider.service.AiProviderService;
import com.agent.dock.provider.service.CliStatusService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai-providers")
@RequiredArgsConstructor
public class AiProviderController {
    private final AiProviderService service;
    private final CliStatusService cliStatusService;

    /** 등록된 런타임 목록. 연결 상태는 워크스페이스별(`/workspaces/{id}/runtimes`)이다. */
    @GetMapping
    public List<AiProviderResponse> findAll() {
        return service.findAll();
    }

    /** 런타임별 CLI 상태를 한 번에(카드마다 CLI 를 실행하지 않게). */
    @GetMapping("/cli")
    public List<ProviderCliStatusResponse> cliAll() {
        return cliStatusService.checkAll();
    }

    /** 등록 가능한 런타임 카탈로그(키·표시 이름·이미 등록됨 여부·기본 capabilities). */
    @GetMapping("/catalog")
    public List<ProviderCatalogEntry> catalog() {
        return service.catalog();
    }

    /** 이 런타임 CLI 의 실행 파일 경로/버전/실행 가능 여부. 폴더와 무관한 런타임 전역 정보다. */
    @GetMapping("/{id}/cli")
    public CliStatusResponse cli(@PathVariable Long id) {
        return cliStatusService.check(id);
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

    /** 런타임 표시 이름 변경. */
    @PutMapping("/{id}")
    public AiProviderResponse rename(@PathVariable Long id, @Valid @RequestBody UpdateAiProviderRequest request) {
        return service.rename(id, request.name());
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

    /** capabilities 를 런타임 기본값으로 되돌린다. */
    @PostMapping("/{id}/capabilities/reset")
    @ResponseStatus(HttpStatus.CREATED)
    public AiProviderResponse resetCapabilities(@PathVariable Long id) {
        return service.resetCapabilities(id);
    }
}
