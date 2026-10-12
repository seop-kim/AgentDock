package com.agent.dock.execution.domain;

/**
 * 판단이 필요한 실행(마스터·그룹 리더)이 출력 끝에 남기는 계약.
 * 위임하면 DELEGATE(위임 대상이 {@code delegatedTargetAgentId} 에 남는다), 사람에게 물으면 ASK(질문은
 * {@code execution.question} 에 남는다), **부모에게** 물으면 ASK_PARENT(사람을 부르지 않고 부모가 답한다), 끝내면 DONE.
 */
public enum ExecutionDecision {
    DELEGATE,
    ASK,
    /** 자식이 사람이 아니라 **부모(판단 실행)** 에게 되묻는 것. 부모의 다음 판단 스텝이 답을 준다. */
    ASK_PARENT,
    DONE
}
