package com.agent.dock.schedule.domain;

/** 놓친 예약 실행 처리 정책. */
public enum MissedPolicy {
    /** 놓친 회차는 건너뛰고 다음 회차부터 실행한다(기본). */
    SKIP,
    /** 몇 회차를 놓쳤든 지금 한 번만 실행한다. */
    RUN_ONCE
}
