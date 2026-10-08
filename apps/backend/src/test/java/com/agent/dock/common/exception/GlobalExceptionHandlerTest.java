package com.agent.dock.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.agent.dock.common.error.ServerErrorLog;
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
    void doesNotRecordStatusExceptions() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/missing");

        ResponseEntity<Map<String, Object>> response = handler.handleUnhandled(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "없음"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(errors.recent()).isEmpty();
    }
}
