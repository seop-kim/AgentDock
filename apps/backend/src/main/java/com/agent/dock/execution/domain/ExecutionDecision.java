package com.agent.dock.execution.domain;

/**
 * 판단이 필요한 실행(마스터·그룹 리더)이 출력 끝에 남기는 계약.
 * 위임하면 DELEGATE(위임 대상이 {@code delegatedTargetAgentId} 에 남는다), 사람에게 물으면 ASK(질문은
 * {@code execution.question} 에 남는다), 끝내면 DONE.
 */
public enum ExecutionDecision {
    DELEGATE,
    ASK,
    DONE
}
