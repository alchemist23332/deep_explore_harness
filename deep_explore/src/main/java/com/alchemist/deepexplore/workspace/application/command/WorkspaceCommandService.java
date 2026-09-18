package com.alchemist.deepexplore.workspace.application.command;

import com.alchemist.deepexplore.workspace.application.WorkspaceOperationException;
import com.alchemist.deepexplore.workspace.application.lifecycle.WorkspaceOperationCoordinator;
import com.alchemist.deepexplore.workspace.application.query.WorkspaceQueryService;
import com.alchemist.deepexplore.workspace.domain.CommandResult;
import com.alchemist.deepexplore.workspace.domain.Workspace;
import com.alchemist.deepexplore.workspace.domain.WorkspaceStatus;
import com.alchemist.deepexplore.workspace.port.SandboxRuntime;
import com.alchemist.deepexplore.workspace.port.WorkspaceStorage;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceCommandService {

    private final WorkspaceQueryService queries;
    private final WorkspaceStorage storage;
    private final SandboxRuntime runtime;
    private final WorkspaceOperationCoordinator operations;

    public WorkspaceCommandService(
            WorkspaceQueryService queries,
            WorkspaceStorage storage,
            SandboxRuntime runtime,
            WorkspaceOperationCoordinator operations
    ) {
        this.queries = queries;
        this.storage = storage;
        this.runtime = runtime;
        this.operations = operations;
    }

    public CommandResult execute(
            String workspaceId,
            String command,
            String relativeWorkingDirectory
    ) {
        return operations.withWriteLock(workspaceId, () -> {
            Workspace workspace = queries.get(workspaceId);
            if (workspace.status() != WorkspaceStatus.RUNNING) {
                throw new WorkspaceOperationException(
                        "SANDBOX_NOT_RUNNING",
                        "Start the workspace sandbox before running commands"
                );
            }
            return runtime.execute(
                    workspace.containerId(),
                    command,
                    storage.containerWorkingDirectory(
                            workspaceId,
                            relativeWorkingDirectory
                    )
            );
        });
    }
}
