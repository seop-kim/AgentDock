-- 프로젝트 ↔ 워크스페이스 N:N. 프로젝트당 기본 워크스페이스는 1개만 둔다.
CREATE TABLE project_workspace (
    id           BIGSERIAL PRIMARY KEY,
    project_id   BIGINT NOT NULL REFERENCES project(id) ON DELETE CASCADE,
    workspace_id BIGINT NOT NULL REFERENCES workspace(id),
    is_default   BOOLEAN NOT NULL DEFAULT false,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT project_workspace_unique UNIQUE (project_id, workspace_id)
);
CREATE UNIQUE INDEX project_workspace_default_unique ON project_workspace (project_id) WHERE is_default;

-- 기존 1:1 할당(project.workspace_id)을 기본 워크스페이스로 옮긴다.
INSERT INTO project_workspace (project_id, workspace_id, is_default)
SELECT id, workspace_id, true FROM project;

ALTER TABLE project DROP COLUMN workspace_id;

-- 에이전트는 프로젝트 소속이고, 고유 페르소나(성격/일하는 방식) 프롬프트를 갖는다.
ALTER TABLE agent ADD COLUMN project_id BIGINT NOT NULL REFERENCES project(id);
ALTER TABLE agent ADD COLUMN persona TEXT;
