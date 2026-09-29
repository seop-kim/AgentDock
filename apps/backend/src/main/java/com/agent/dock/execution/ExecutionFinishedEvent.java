package com.agent.dock.execution;

/**
 * 실행이 끝났을 때 Task 상태를 갱신하기 위한 이벤트.
 * execution 패키지가 task 패키지를 참조하지 않도록 이벤트로 분리한다.
 */
public record ExecutionFinishedEvent(Long executionId, Long taskId, ExecutionStatus status) {}
