CREATE TABLE agent_runs (
    id VARCHAR(100) PRIMARY KEY,
    conversation_id VARCHAR(100) NOT NULL
        REFERENCES conversations (id) ON DELETE CASCADE,
    user_message_id VARCHAR(100) NOT NULL,
    assistant_message_id VARCHAR(100) NOT NULL,
    agent_id VARCHAR(100) NOT NULL,
    profile_id VARCHAR(100) NOT NULL,
    status VARCHAR(30) NOT NULL,
    error_code VARCHAR(100),
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT agent_runs_status_check
        CHECK (status IN (
            'RUNNING',
            'COMPLETED',
            'FAILED',
            'CANCELLED',
            'WAITING_APPROVAL'
        ))
);

CREATE INDEX agent_runs_conversation_created_idx
    ON agent_runs (conversation_id, created_at DESC);

CREATE INDEX agent_runs_status_idx
    ON agent_runs (status, created_at);

CREATE TABLE agent_run_events (
    event_id VARCHAR(100) PRIMARY KEY,
    run_id VARCHAR(100) NOT NULL
        REFERENCES agent_runs (id) ON DELETE CASCADE,
    sequence_no BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload_json JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT agent_run_events_sequence_unique
        UNIQUE (run_id, sequence_no)
);

CREATE INDEX agent_run_events_run_sequence_idx
    ON agent_run_events (run_id, sequence_no);

CREATE TABLE run_checkpoints (
    run_id VARCHAR(100) NOT NULL
        REFERENCES agent_runs (id) ON DELETE CASCADE,
    version BIGINT NOT NULL,
    state_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (run_id, version)
);
