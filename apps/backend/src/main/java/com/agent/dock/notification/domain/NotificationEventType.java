package com.agent.dock.notification.domain;

/** 알림을 보낼 사건. 규칙(notification_rule)이 이 값으로 채널을 고른다. */
public enum NotificationEventType {
    /** 실행 트리가 끝났다(성공). */
    EXECUTION_FINISHED,
    /** 실행 트리가 실패로 끝났다. */
    EXECUTION_FAILED,
    /** 판단 실행이 사람의 답을 기다린다(WAITING_INPUT). */
    WAITING_INPUT,
    /** 프로젝트 예산이 alertRatio(기본 80%)를 넘었다. */
    BUDGET_THRESHOLD
}