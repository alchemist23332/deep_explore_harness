CREATE TABLE workspaces (
    id VARCHAR(100) PRIMARY KEY,
    owner_id VARCHAR(100) NOT NULL,
    name VARCHAR(80) NOT NULL,
    runtime_profile VARCHAR(50) NOT NULL,
    container_id VARCHAR(100),
    status VARCHAR(30) NOT NULL DEFAULT 'STOPPED',
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_started_at TIMESTAMPTZ,
    CONSTRAINT workspaces_status_check
        CHECK (status IN (
            'STOPPED',
            'STARTING',
            'RUNNING',
            'STOPPING',
            'ERROR'
        ))
);

CREATE INDEX workspaces_owner_updated_idx
    ON workspaces (owner_id, updated_at DESC);

CREATE UNIQUE INDEX workspaces_container_id_unique
    ON workspaces (container_id)
    WHERE container_id IS NOT NULL;
