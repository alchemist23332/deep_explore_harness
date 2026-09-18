package com.alchemist.deepexplore.workspace.application.terminal;

import com.alchemist.deepexplore.workspace.application.WorkspaceOperationException;
import com.alchemist.deepexplore.workspace.application.lifecycle.WorkspaceOperationCoordinator;
import com.alchemist.deepexplore.workspace.domain.Workspace;
import com.alchemist.deepexplore.workspace.domain.WorkspaceStatus;
import com.alchemist.deepexplore.workspace.port.SandboxTerminal;
import com.alchemist.deepexplore.workspace.application.query.WorkspaceQueryService;
import org.springframework.stereotype.Service;

@Service
public class TerminalApplicationService {

    private final WorkspaceQueryService workspaces;
    private final TerminalSessionRegistry sessions;
    private final WorkspaceOperationCoordinator operations;

    public TerminalApplicationService(
            WorkspaceQueryService workspaces,
            TerminalSessionRegistry sessions,
            WorkspaceOperationCoordinator operations
    ) {
        this.workspaces = workspaces;
        this.sessions = sessions;
        this.operations = operations;
    }

    public TerminalConnection open(
            String workspaceId,
            int columns,
            int rows
    ) {
        SandboxTerminal.Session session = operations.withWriteLock(
                workspaceId,
                () -> openSession(workspaceId, columns, rows)
        );
        return new TerminalConnection() {
            @Override
            public String id() {
                return session.id();
            }

            @Override
            public reactor.core.publisher.Flux<byte[]> output() {
                return session.output();
            }

            @Override
            public void input(byte[] data) {
                operations.withWriteLock(workspaceId, () -> {
                    session.input(data);
                    return null;
                });
            }

            @Override
            public void resize(int nextColumns, int nextRows) {
                operations.withWriteLock(workspaceId, () -> {
                    session.resize(nextColumns, nextRows);
                    return null;
                });
            }

            @Override
            public void close() {
                operations.withWriteLock(workspaceId, () -> {
                    session.close();
                    return null;
                });
            }
        };
    }

    private SandboxTerminal.Session openSession(
            String workspaceId,
            int columns,
            int rows
    ) {
        Workspace workspace = workspaces.get(workspaceId);
        if (workspace.status() != WorkspaceStatus.RUNNING) {
            throw new WorkspaceOperationException(
                    "SANDBOX_NOT_RUNNING",
                    "Start the workspace sandbox before opening a terminal"
            );
        }
        return sessions.open(
                workspace.id(),
                workspace.containerId(),
                columns,
                rows
        );
    }
}
