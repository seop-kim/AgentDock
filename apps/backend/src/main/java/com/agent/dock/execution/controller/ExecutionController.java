package com.agent.dock.execution.controller;

import com.agent.dock.delegation.service.DelegationService;
import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.dto.AnswerRequest;
import com.agent.dock.execution.dto.ExecutionLogResponse;
import com.agent.dock.execution.dto.ExecutionResponse;
import com.agent.dock.execution.dto.ExecutionTreeResponse;
import com.agent.dock.execution.service.ExecutionService;
import jakarta.validation.Valid;
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
    /** 답을 받아 **실행을 이어서 돌리는** 일은 오케스트레이터(위임 루프)의 것이다. */
    private final DelegationService delegationService;

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

    /**
     * 자동 병합이 MANUAL 로 끝난 트리를 **다시 병합**한다(사람이 변경을 정리한 뒤 부른다).
     * 도는 실행이면 409, 워크트리 브랜치가 없으면 404. 결과는 실행에 다시 기록된다.
     */
    @PostMapping("/{id}/merge")
    @ResponseStatus(HttpStatus.CREATED)
    public ExecutionResponse retryMerge(@PathVariable Long id) {
        return service.retryMerge(id);
    }

    /**
     * `WAITING_INPUT` 인 실행에 사람의 답을 넣고 그 실행의 판단 루프를 **이어서** 돌린다.
     * 입력 대기 상태가 아니면 409, 없는 실행이면 404.
     */
    @PostMapping("/{id}/answer")
    @ResponseStatus(HttpStatus.CREATED)
    public ExecutionResponse answer(@PathVariable Long id, @Valid @RequestBody AnswerRequest request) {
        return delegationService.answer(id, request.text());
    }
}
