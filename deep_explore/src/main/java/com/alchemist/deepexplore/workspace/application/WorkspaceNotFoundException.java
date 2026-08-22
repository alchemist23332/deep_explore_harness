package com.alchemist.deepexplore.workspace.application;

public class WorkspaceNotFoundException extends RuntimeException {

    public WorkspaceNotFoundException(String workspaceId) {
        super("Workspace not found: " + workspaceId);
    }
}
