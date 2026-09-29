CREATE TABLE ai_provider (
    id BIGSERIAL PRIMARY KEY,
    key VARCHAR(32) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    capabilities JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE ai_connection (
    id BIGSERIAL PRIMARY KEY,
    provider_id BIGINT NOT NULL REFERENCES ai_provider(id),
    account_name VARCHAR(255),
    credential_reference VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'DISCONNECTED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE agent_role (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE permission_profile (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    file_read BOOLEAN NOT NULL DEFAULT false,
    file_write BOOLEAN NOT NULL DEFAULT false,
    terminal_execute BOOLEAN NOT NULL DEFAULT false,
    git_status BOOLEAN NOT NULL DEFAULT true,
    git_diff BOOLEAN NOT NULL DEFAULT false,
    git_commit BOOLEAN NOT NULL DEFAULT false,
    git_push BOOLEAN NOT NULL DEFAULT false,
    db_read BOOLEAN NOT NULL DEFAULT false,
    db_write BOOLEAN NOT NULL DEFAULT false,
    db_schema_change BOOLEAN NOT NULL DEFAULT false,
    deploy BOOLEAN NOT NULL DEFAULT false,
    external_network_access BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE workspace (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    path VARCHAR(1024) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE agent (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    role_id BIGINT NOT NULL REFERENCES agent_role(id),
    permission_profile_id BIGINT NOT NULL REFERENCES permission_profile(id),
    provider_id BIGINT NOT NULL REFERENCES ai_provider(id),
    connection_id BIGINT REFERENCES ai_connection(id),
    workspace_id BIGINT REFERENCES workspace(id),
    model VARCHAR(255),
    mode VARCHAR(64),
    profile JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE execution (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL REFERENCES agent(id),
    workspace_id BIGINT NOT NULL REFERENCES workspace(id),
    prompt TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    started_at TIMESTAMPTZ,
    finished_at TIMESTAMPTZ,
    exit_code INTEGER,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE execution_log (
    id BIGSERIAL PRIMARY KEY,
    execution_id BIGINT NOT NULL REFERENCES execution(id),
    stream VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_execution_log_execution_id ON execution_log(execution_id);
