-- 알림 규칙 (멱등: 이미 적용된 DB 에서 다시 실행해도 안전하다)
--
-- 어떤 사건(event_type)이 났을 때 어느 채널(channel_id)로 보낼지 잇는다. project_id 가 NULL 이면
-- 전역 규칙(모든 프로젝트), 값이 있으면 그 프로젝트 규칙이다. 채널을 지우면 규칙도 함께 지운다
-- (channel_id ON DELETE CASCADE). 사건 종류: EXECUTION_FINISHED·EXECUTION_FAILED·WAITING_INPUT·BUDGET_THRESHOLD.
CREATE TABLE IF NOT EXISTS notification_rule (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT REFERENCES project(id) ON DELETE CASCADE,
    channel_id BIGINT NOT NULL REFERENCES notification_channel(id) ON DELETE CASCADE,
    event_type VARCHAR(40) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_notification_rule_project ON notification_rule(project_id);
CREATE INDEX IF NOT EXISTS idx_notification_rule_channel ON notification_rule(channel_id);