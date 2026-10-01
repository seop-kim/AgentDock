package com.agent.dock.execution.domain;

public enum ExecutionStatus {
    PENDING,
    RUNNING,
    /** 위임한 자식 실행들이 끝나기를 기다리는 중(부모가 멈춰 있다). */
    WAITING_CHILD,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
