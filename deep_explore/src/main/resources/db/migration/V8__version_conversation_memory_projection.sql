ALTER TABLE conversation_memory
    ADD COLUMN source_head_message_id VARCHAR(100),
    ADD COLUMN format_version INTEGER NOT NULL DEFAULT 1;
