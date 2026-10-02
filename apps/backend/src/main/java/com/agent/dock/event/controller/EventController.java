package com.agent.dock.event.controller;

import com.agent.dock.event.service.EventStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 전역 데이터 변경 스트림. 화면은 이 연결 하나로 "무엇이 바뀌었는지"를 받고(폴링 대신),
 * 바뀐 조각만 기존 REST 엔드포인트로 다시 읽는다.
 *
 * <p>연결 직후 `hello` 이벤트 한 번, 이후에는 데이터 이벤트(`{"type":…,"projectId":…,"executionId":…,"taskId":…}`)와
 * 25초마다 하트비트 주석 줄이 흐른다. 실행 로그 스트림(`/executions/{id}/stream`)은 그대로 따로 있다.
 */
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {
    private final EventStreamService streamService;

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return streamService.subscribe();
    }
}
