CREATE TABLE conversations (
    id VARCHAR(100) PRIMARY KEY,
    title VARCHAR(80),
    status VARCHAR(20) NOT NULL DEFAULT 'REGULAR',
    generation_status VARCHAR(20) NOT NULL DEFAULT 'IDLE',
    generation_started_at TIMESTAMPTZ,
    head_message_id VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT conversations_status_check
        CHECK (status IN ('REGULAR', 'ARCHIVED')),
    CONSTRAINT conversations_generation_status_check
        CHECK (generation_status IN ('IDLE', 'RUNNING'))
);

CREATE INDEX conversations_updated_at_idx
    ON conversations (updated_at DESC);

CREATE TABLE messages (
    id VARCHAR(100) PRIMARY KEY,
    conversation_id VARCHAR(100) NOT NULL
        REFERENCES conversations (id) ON DELETE CASCADE,
    parent_message_id VARCHAR(100),
    sequence_no BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETE',
    model VARCHAR(200),
    token_usage INTEGER,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT messages_role_check
        CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT messages_status_check
        CHECK (status IN ('COMPLETE', 'INCOMPLETE')),
    CONSTRAINT messages_conversation_sequence_unique
        UNIQUE (conversation_id, sequence_no)
);

CREATE INDEX messages_conversation_created_idx
    ON messages (conversation_id, sequence_no);

CREATE TABLE conversation_memory (
    conversation_id VARCHAR(100) PRIMARY KEY
        REFERENCES conversations (id) ON DELETE CASCADE,
    messages_json JSONB NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
