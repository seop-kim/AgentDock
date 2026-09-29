CREATE TABLE project (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    workspace_id BIGINT NOT NULL REFERENCES workspace(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 'group' 은 SQL 예약어라 agent_group 을 쓴다
CREATE TABLE agent_group (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES project(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    leader_agent_id BIGINT REFERENCES agent(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT agent_group_project_name_key UNIQUE (project_id, name)
);

CREATE TABLE agent_group_member (
    id BIGSERIAL PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES agent_group(id),
    agent_id BIGINT NOT NULL REFERENCES agent(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT agent_group_member_key UNIQUE (group_id, agent_id)
);

CREATE TABLE task (
    id BIGSERIAL PRIMARY KEY,
    project_id BIGINT NOT NULL REFERENCES project(id),
    group_id BIGINT REFERENCES agent_group(id),
    agent_id BIGINT REFERENCES agent(id),
    title VARCHAR(255) NOT NULL,
    prompt TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT task_assignee_check CHECK ((group_id IS NULL) <> (agent_id IS NULL))
);

-- 컬럼을 지우면 그 컬럼에 딸린 FK 도 함께 삭제된다
ALTER TABLE agent DROP COLUMN workspace_id;

ALTER TABLE execution ADD COLUMN task_id BIGINT REFERENCES task(id);
