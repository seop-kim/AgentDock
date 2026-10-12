package com.agent.dock.event.dto;

/**
 * 전역 스트림({@code GET /events/stream})이 보내는 알림 종류.
 * 값은 화면(프론트엔드)이 "어느 조각을 다시 읽을지" 고르는 키이므로 문자열을 그대로 계약으로 쓴다.
 */
public final class EventTypes {
    /** 실행 트리(상태·계약·결과·워크트리). */
    public static final String EXECUTION_CHANGED = "execution.changed";
    /** Task(생성·상태). */
    public static final String TASK_CHANGED = "task.changed";
    /** 에이전트(생성·수정·삭제·런타임 재할당·배치). */
    public static final String AGENT_CHANGED = "agent.changed";
    /** 그룹(팀)과 멤버. */
    public static final String GROUP_CHANGED = "group.changed";
    /** 프로젝트(이름·설명·마스터·워크스페이스 할당·구성도 배치). */
    public static final String PROJECT_CHANGED = "project.changed";
    /** 워크스페이스(폴더 등록). */
    public static final String WORKSPACE_CHANGED = "workspace.changed";
    /** AI 런타임(등록·on/off·삭제·모델/모드). */
    public static final String RUNTIME_CHANGED = "runtime.changed";
    /** 파일 첨부(업로드·Task 연결). */
    public static final String ATTACHMENT_CHANGED = "attachment.changed";
    /** 알림 채널·규칙(설정). */
    public static final String NOTIFICATION_CHANGED = "notification.changed";
    /** 비용 예산(설정·경고). */
    public static final String BUDGET_CHANGED = "budget.changed";

    /** 예약(반복·예약 실행) 생성·수정·삭제·실행. */
    public static final String SCHEDULE_CHANGED = "schedule.changed";

    /** 전역 설정(병합 정책 등 — 프로젝트에 매이지 않는 설정). */
    public static final String SETTINGS_CHANGED = "settings.changed";

    private EventTypes() {
    }
}