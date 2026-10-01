package com.agent.dock.agent.controller;

import com.agent.dock.agent.domain.Agent;
import com.agent.dock.agent.dto.AgentResponse;
import com.agent.dock.agent.dto.AssignProviderRequest;
import com.agent.dock.agent.dto.CreateAgentRequest;
import com.agent.dock.agent.dto.UpdateAgentRequest;
import com.agent.dock.agent.service.AgentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

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

    /** 에이전트 수정(이름·역할·권한·런타임·모델·모드·페르소나). */
    @PutMapping("/{id}")
    public AgentResponse update(@PathVariable Long id, @Valid @RequestBody UpdateAgentRequest request) {
        return service.update(id, request);
    }

    /** 에이전트 삭제. 마스터는 409 로 거부한다. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
