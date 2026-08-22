package com.alchemist.deepexplore.workspace.adapter.in.web;

import com.alchemist.deepexplore.workspace.application.WorkspaceNotFoundException;
import com.alchemist.deepexplore.workspace.application.WorkspaceOperationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = WorkspaceController.class)
public class WorkspaceExceptionHandler {

    @ExceptionHandler(WorkspaceNotFoundException.class)
    public ResponseEntity<WorkspaceWebModels.ErrorResponse> notFound(
            WorkspaceNotFoundException error
    ) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new WorkspaceWebModels.ErrorResponse(
                        "WORKSPACE_NOT_FOUND",
                        error.getMessage()
                )
        );
    }

    @ExceptionHandler(WorkspaceOperationException.class)
    public ResponseEntity<WorkspaceWebModels.ErrorResponse> operationFailed(
            WorkspaceOperationException error
    ) {
        HttpStatus status = switch (error.code()) {
            case "DOCKER_UNAVAILABLE" -> HttpStatus.SERVICE_UNAVAILABLE;
            case "SANDBOX_NOT_RUNNING",
                 "WORKSPACE_ENTRY_CONFLICT",
                 "WORKSPACE_DIRECTORY_NOT_EMPTY",
                 "TERMINAL_SESSION_LIMIT" -> HttpStatus.CONFLICT;
            case "WORKSPACE_ENTRY_NOT_FOUND",
                 "WORKSPACE_DIRECTORY_NOT_FOUND",
                 "WORKSPACE_FILE_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(
                new WorkspaceWebModels.ErrorResponse(
                        error.code(),
                        error.getMessage()
                )
        );
    }
}
