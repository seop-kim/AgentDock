-- 목업 화면에 맞추기 위한 보강 (멱등: 이미 적용된 DB 에서 다시 실행해도 안전하다)
--
-- 1) 구성도 배치: 에이전트가 캔버스에 놓였는지(placed)와 손으로 옮긴 좌표, 그룹 상자의 좌표.
-- 2) 파일 첨부: 첨부는 워크스페이스 폴더 안 .agentdock/attachments 로 복사되고, 그 기록을 여기 남긴다.

ALTER TABLE agent ADD COLUMN IF NOT EXISTS placed BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE agent ADD COLUMN IF NOT EXISTS node_x DOUBLE PRECISION;
ALTER TABLE agent ADD COLUMN IF NOT EXISTS node_y DOUBLE PRECISION;

ALTER TABLE agent_group ADD COLUMN IF NOT EXISTS node_x DOUBLE PRECISION;
ALTER TABLE agent_group ADD COLUMN IF NOT EXISTS node_y DOUBLE PRECISION;

CREATE TABLE IF NOT EXISTS attachment (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL REFERENCES workspace(id),
    task_id BIGINT REFERENCES task(id),
    original_name VARCHAR(512) NOT NULL,
    stored_path VARCHAR(1024) NOT NULL,
    size_bytes BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_attachment_task ON attachment(task_id);
CREATE INDEX IF NOT EXISTS idx_attachment_workspace ON attachment(workspace_id);
