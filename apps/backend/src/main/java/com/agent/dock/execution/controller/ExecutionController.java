package com.agent.dock.execution.controller;

import com.agent.dock.delegation.service.DelegationService;
import com.agent.dock.execution.domain.Execution;
import com.agent.dock.execution.dto.AnswerRequest;
import com.agent.dock.execution.dto.ExecutionDiffResponse;
import com.agent.dock.execution.dto.ExecutionLogResponse;
import com.agent.dock.execution.dto.ExecutionResponse;
import com.agent.dock.execution.dto.ExecutionStatsResponse;
import com.agent.dock.execution.dto.ExecutionSummaryResponse;
import com.agent.dock.execution.dto.ExecutionTreeResponse;
import com.agent.dock.execution.service.ExecutionDiffService;
import com.agent.dock.execution.service.ExecutionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
    /** 트리 루트의 변경 파일을 파일별 unified diff 로 만든다(무거운 git 은 workers 풀에서 돈다). */
    private final ExecutionDiffService diffService;
    /** 답을 받아 **실행을 이어서 돌리는** 일은 오케스트레이터(위임 루프)의 것이다. */
    private final DelegationService delegationService;

    /** 실행 목록(대시보드·Tasks/실행 목록·요청 창). projectId·status 는 선택, 최신순, beforeId 커서. */
    @GetMapping
    public List<ExecutionSummaryResponse> findAll(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long beforeId) {
        return service.listSummaries(projectId, status, limit, beforeId);
    }

    /** 실행 집계(대시보드 사용량). projectId 를 주면 그 프로젝트만, 없으면 전역. */
    @GetMapping("/summary")
    public ExecutionStatsResponse summary(@RequestParam(required = false) Long projectId) {
        return service.stats(projectId);
    }

    /**
     * 여러 실행의 로그를 한 연결로 받는다(터미널을 여러 개 열어도 SSE 는 하나).
     * ?ids=1,2 — 쉼표 목록. 이미 끝난 실행은 저장 로그를 재생하고 그 실행의 exit 만 보낸다.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamMany(@RequestParam(required = false) String ids) {
        return service.streamMany(ids);
    }

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

    /**
     * 실행 트리 루트가 바꾼 파일별 unified diff(변경 파일 창). git 은 workers 풀에서 돌아 요청 스레드를 막지 않는다.
     * 트리 루트에 커밋이 없으면 available=false 와 사유를 싣는다.
     */
    @GetMapping("/{id}/diff")
    public CompletableFuture<ExecutionDiffResponse> diff(@PathVariable Long id) {
        return diffService.diff(id);
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
