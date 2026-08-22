package com.alchemist.deepexplore.workspace.application;

public class WorkspaceOperationException extends RuntimeException {

    private final String code;

    public WorkspaceOperationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public WorkspaceOperationException(
            String code,
            String message,
            Throwable cause
    ) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
