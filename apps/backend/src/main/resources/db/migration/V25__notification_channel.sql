-- 알림 채널 (멱등: 이미 적용된 DB 에서 다시 실행해도 안전하다)
--
-- 웹훅(WEBHOOK)·Slack(SLACK)·메일(EMAIL) 세 종류의 발송 대상을 담는다. project_id 가 NULL 이면
-- 전역 채널(모든 프로젝트에서 쓸 수 있다)이고, 값이 있으면 그 프로젝트 전용이다.
-- config 는 종류별 발송 설정을 담는 JSONB 다(웹훅/Slack: url·webhookUrl, 메일: to·from 등).
CREATE TABLE IF NOT EXISTS notification_channel (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT REFERENCES project(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL,
    name VARCHAR(200) NOT NULL,
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_notification_channel_project ON notification_channel(project_id);