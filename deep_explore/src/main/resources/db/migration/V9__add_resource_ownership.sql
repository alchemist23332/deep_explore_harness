ALTER TABLE conversations
    ADD COLUMN tenant_id VARCHAR(100) NOT NULL DEFAULT 'local',
    ADD COLUMN owner_id VARCHAR(100) NOT NULL DEFAULT 'local-user';

ALTER TABLE workspaces
    ADD COLUMN tenant_id VARCHAR(100) NOT NULL DEFAULT 'local';

CREATE INDEX conversations_tenant_owner_updated_idx
    ON conversations (tenant_id, owner_id, updated_at DESC);

CREATE INDEX workspaces_tenant_owner_updated_idx
    ON workspaces (tenant_id, owner_id, updated_at DESC);
