package com.agent.dock.execution;

/**
 * 판단이 필요한 실행(마스터·그룹 리더)이 출력 끝에 남기는 계약.
 * 위임하면 DELEGATE(위임 대상이 {@code delegatedTargetAgentId} 에 남는다), 끝내면 DONE.
 */
public enum ExecutionDecision {
    DELEGATE,
    DONE
}
