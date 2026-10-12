-- 반복·예약 실행(schedule) — 명령을 주기(cron)에 따라 자동으로 실행한다 (멱등: 다시 실행해도 안전하다)
--
-- "명령 하나 = Task 하나 = 실행 트리 하나" 규칙은 그대로다. 예약은 그 명령을 **언제** 보낼지만 정하고,
-- 실제 실행은 기존 채팅 명령 경로(POST /projects/{id}/commands → TaskService.command → DelegationService.start)를
-- 그대로 재사용한다(예약 전용 실행 경로를 새로 만들지 않는다).
--
-- 주기 표현: Spring CronExpression 6필드(초 분 시 일 월 요일). 예) '0 0 9 * * *' = 매일 09:00.
-- next_run_at 을 저장해 두고 스케줄러가 주기적으로 훑는다 — 앱이 멈춰 있던 동안 놓친 실행은
-- missed_policy(SKIP = 건너뛴다 / RUN_ONCE = 밀린 것을 한 번만 지금 실행)로 처리한다.
--
-- FK: 프로젝트가 지워지면 예약도 함께 사라진다(CASCADE). 대상 에이전트·그룹·마지막 Task 가 지워지면
-- 참조만 비운다(SET NULL) — 그래서 다른 도메인의 삭제 코드를 고칠 필요가 없다.
CREATE TABLE schedule (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    prompt TEXT NOT NULL,
    target_agent_id BIGINT REFERENCES agent(id) ON DELETE SET NULL,
    group_id BIGINT REFERENCES agent_group(id) ON DELETE SET NULL,
    cron VARCHAR(120) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    missed_policy VARCHAR(20) NOT NULL DEFAULT 'SKIP',
    next_run_at TIMESTAMPTZ,
    last_run_at TIMESTAMPTZ,
    last_task_id BIGINT REFERENCES task(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_schedule_project ON schedule(project_id);
CREATE INDEX IF NOT EXISTS idx_schedule_due ON schedule(next_run_at);