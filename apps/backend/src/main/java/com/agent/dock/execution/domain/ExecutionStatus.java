package com.agent.dock.execution.domain;

public enum ExecutionStatus {
    PENDING,
    RUNNING,
    /** 위임한 자식 실행들이 끝나기를 기다리는 중(부모가 멈춰 있다). */
    WAITING_CHILD,
    /** 판단 중 사람의 답이 필요해 멈춰 있다(실행은 끝나지 않았다 — `POST /executions/{id}/answer` 로 이어서 돈다). */
    WAITING_INPUT,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
