package com.agent.dock.common.exception;

import com.agent.dock.common.error.ServerErrorLog;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class GlobalExceptionHandler {

    private final ServerErrorLog errors;

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(BadRequestException ex) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(NotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(ForbiddenException ex) {
        return body(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(ConflictException ex) {
        return body(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return body(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return body(HttpStatus.BAD_REQUEST, "Invalid path parameter: " + ex.getName());
    }

    /** 본문 JSON 을 읽지 못한 경우도 **사용자 입력 문제**다 — 내부 오류로 담지 않는다. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadableBody(HttpMessageNotReadableException ex) {
        return body(HttpStatus.BAD_REQUEST, "요청 본문을 읽지 못했습니다");
    }

    /**
     * 미처리 예외(5xx). **화면에서 원인을 볼 수 있게 담아 두고** 그대로 알려 준다 —
     * 자가호스팅이라 원인이 보이는 편이 낫고, 사람이 여기서 "에이전트에게 수정 맡기기"를 고를 수 있다.
     * 상태 코드를 코드로 정한 예외(ResponseStatusException, 4xx 포함)는 내부 오류가 아니므로 담지 않는다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnhandled(Exception ex, HttpServletRequest request) {
        if (ex instanceof ResponseStatusException statusException) {
            String reason = statusException.getReason();
            return body(HttpStatus.valueOf(statusException.getStatusCode().value()),
                    reason == null || reason.isBlank() ? statusException.getMessage() : reason);
        }
        ServerErrorLog.Entry entry = errors.record(request.getMethod(), request.getRequestURI(), ex);
        log.error("unhandled error #{} {} {}: {}", entry.id(), entry.method(), entry.path(), entry.message(), ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR,
                "%s: %s".formatted(ex.getClass().getSimpleName(), ex.getMessage()));
    }

    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of(
                "statusCode", status.value(),
                "error", status.getReasonPhrase(),
                "message", message
        ));
    }
}
