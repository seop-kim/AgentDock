package com.agent.dock.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.agent.dock.common.error.ServerErrorLog;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

/**
 * 미처리 예외만 기록되고, 사용자 입력 문제(상태를 코드로 정한 예외)는 기록되지 않아야 한다 —
 * 화면의 확인 창이 소음으로 뜨면 아무도 안 본다.
 */
class GlobalExceptionHandlerTest {

    private final ServerErrorLog errors = new ServerErrorLog();
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(errors);

    @Test
    void recordsUnhandledExceptionAndReturns500() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/projects/1/commands");

        ResponseEntity<Map<String, Object>> response =
                handler.handleUnhandled(new IllegalStateException("boom"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).containsEntry("message", "IllegalStateException: boom");
        assertThat(errors.recent()).hasSize(1);
        assertThat(errors.recent().get(0).method()).isEqualTo("POST");
        assertThat(errors.recent().get(0).path()).isEqualTo("/projects/1/commands");
    }

    @Test
    void doesNotRecordDisconnectedClient() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/events/stream");
        // 실제로 올라오던 스택: AsyncRequestNotUsableException(연결 끊김) → 원인 IOException(연결 중단).
        // 탭을 닫거나 새로고침할 때마다 이게 내부 오류로 기록되면 창이 계속 뜬다 — 기록하면 안 된다.
        IOException broken = new IOException("현재 연결은 사용자의 호스트 시스템의 소프트웨어의 의해 중단되었습니다");

        ResponseEntity<Map<String, Object>> response =
                handler.handleUnhandled(new RuntimeException("disconnected", broken), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(errors.recent()).isEmpty();
    }

    @Test
    void doesNotRecordStatusExceptions() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/missing");

        ResponseEntity<Map<String, Object>> response = handler.handleUnhandled(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "없음"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(errors.recent()).isEmpty();
    }
}
