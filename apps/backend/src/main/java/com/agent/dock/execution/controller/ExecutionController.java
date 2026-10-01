package com.agent.dock.execution.controller;

import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.dto.ExecutionLogResponse;
import com.agent.dock.execution.dto.ExecutionResponse;
import com.agent.dock.execution.dto.ExecutionTreeResponse;
import com.agent.dock.execution.service.ExecutionService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/executions")
@RequiredArgsConstructor
public class ExecutionController {
    private final ExecutionService service;

    @GetMapping("/{id}")
    public ExecutionResponse findOne(@PathVariable Long id) {
        return service.findOne(id);
    }

    @GetMapping("/{id}/logs")
    public List<ExecutionLogResponse> getLogs(@PathVariable Long id) {
        return service.getLogs(id);
    }

    @GetMapping("/{id}/tree")
    public ExecutionTreeResponse getTree(@PathVariable Long id) {
        return service.getTree(id);
    }

    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String id) {
        return service.streamLogs(id);
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Boolean> cancel(@PathVariable Long id) {
        return service.cancel(id);
    }

    /**
     * 워크트리 정리(사람이 판단해 부른다). `?branch=true` 면 전용 브랜치도 함께 지운다.
     * 아직 도는 실행이면 409 — 자동 삭제는 없다.
     */
    @DeleteMapping("/{id}/worktree")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeWorktree(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean branch) {
        service.removeWorktree(id, branch);
    }
}
