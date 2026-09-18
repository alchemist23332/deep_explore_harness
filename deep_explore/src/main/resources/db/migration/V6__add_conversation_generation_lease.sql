ALTER TABLE conversations
    ADD COLUMN generation_owner VARCHAR(100),
    ADD COLUMN generation_version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE conversation_memory
    ADD COLUMN dirty BOOLEAN NOT NULL DEFAULT FALSE;
