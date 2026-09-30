-- 런타임 on/off: 화면(에이전트 설정)에서 켜고 끄는 단위다.
ALTER TABLE ai_provider ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT false;
UPDATE ai_provider SET enabled = true WHERE key = 'CLAUDE_CODE' AND deleted_at IS NULL;

-- 폴더(워크스페이스)별 런타임 상태: 그 폴더를 작업 디렉터리로 CLI 를 실제 실행해 확인한 결과.
-- Provider 단위 Connection 을 대체한다(로그인은 런타임 전역, 상태는 폴더별).
CREATE TABLE workspace_runtime_status (
    id              BIGSERIAL PRIMARY KEY,
    workspace_id    BIGINT NOT NULL REFERENCES workspace(id) ON DELETE CASCADE,
    provider_id     BIGINT NOT NULL REFERENCES ai_provider(id),
    status          VARCHAR(32) NOT NULL DEFAULT 'DISCONNECTED',
    last_checked_at TIMESTAMPTZ,
    last_error      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT workspace_runtime_status_unique UNIQUE (workspace_id, provider_id)
);
