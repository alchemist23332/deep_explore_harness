package com.alchemist.deepexplore.workspace.application.terminal;

import com.alchemist.deepexplore.workspace.application.WorkspaceOperationException;
import com.alchemist.deepexplore.workspace.domain.Workspace;
import com.alchemist.deepexplore.workspace.domain.WorkspaceStatus;
import com.alchemist.deepexplore.workspace.port.SandboxTerminal;
import com.alchemist.deepexplore.workspace.application.query.WorkspaceQueryService;
import org.springframework.stereotype.Service;

@Service
public class TerminalApplicationService {

    private final WorkspaceQueryService workspaces;
    private final TerminalSessionRegistry sessions;

    public TerminalApplicationService(
            WorkspaceQueryService workspaces,
            TerminalSessionRegistry sessions
    ) {
        this.workspaces = workspaces;
        this.sessions = sessions;
    }

    public TerminalConnection open(
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
        SandboxTerminal.Session session = sessions.open(
                workspace.id(),
                workspace.containerId(),
                columns,
                rows
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
                session.input(data);
            }

            @Override
            public void resize(int nextColumns, int nextRows) {
                session.resize(nextColumns, nextRows);
            }

            @Override
            public void close() {
                session.close();
            }
        };
    }
}
