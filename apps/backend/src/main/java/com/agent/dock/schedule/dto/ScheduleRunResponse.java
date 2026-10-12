package com.agent.dock.schedule.dto;

import java.time.Instant;

/** 예약 즉시 실행 결과 — 만들어진 Task 와 실행 트리 루트. */
public record ScheduleRunResponse(Long scheduleId, Long taskId, Long executionId, Instant ranAt) {
}
