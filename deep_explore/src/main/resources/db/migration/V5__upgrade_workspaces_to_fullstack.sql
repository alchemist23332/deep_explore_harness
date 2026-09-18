UPDATE workspaces
SET runtime_profile = 'FULLSTACK',
    updated_at = CURRENT_TIMESTAMP
WHERE runtime_profile = 'JAVA_21';
