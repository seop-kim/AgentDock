package com.agent.dock.event.dto;

/**
 * 전역 데이터 변경 알림(SSE `GET /events/stream` 의 페이로드).
 *
 * <p>**페이로드가 아니라 알림이다.** 무엇이 바뀌었는지만 알려 주고(타입 + 아는 식별자), 화면은 그 조각만
 * REST 로 다시 읽는다 — 그래야 폴링(3초마다 ~20건)을 이벤트 하나로 대신할 수 있다.
 * 아는 식별자만 싣는다(모르면 null). 예: 실행 상태 변경 = {@code execution.changed} + projectId/executionId/taskId.
 */
public record DataChangedEvent(String type, Long projectId, Long executionId, Long taskId) {
}
