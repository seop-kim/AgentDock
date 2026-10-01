package com.agent.dock.execution.domain;

/**
 * 실행 트리 결과를 메인 저장소로 되돌린 결과.
 *
 * <p>트리(루트 실행)가 끝나면 그 워크트리의 변경을 트리 브랜치(`agentdock/exec-<루트id>`)에 커밋 하나로 남기고,
 * 메인 저장소가 깨끗하면 자동으로 병합한다. 병합하지 못하면 사유를 {@code merge_detail} 에 남기고 사람이 판단한다.
 */
public enum MergeStatus {
    /** 트리 브랜치를 메인 저장소의 현재 브랜치로 자동 병합했다. */
    MERGED,
    /** 자동 병합하지 못했다(메인 트리가 더럽거나 충돌). 사유와 대상 브랜치는 {@code merge_detail} 과 로그에 남는다. */
    MANUAL,
    /** 커밋할 변경이 없었다(빈 커밋을 만들지 않는다). */
    NONE
}
