ALTER TABLE agent_runs
    ADD COLUMN workspace_id VARCHAR(100)
        REFERENCES workspaces (id) ON DELETE SET NULL;

CREATE INDEX agent_runs_workspace_created_idx
    ON agent_runs (workspace_id, created_at DESC)
    WHERE workspace_id IS NOT NULL;
