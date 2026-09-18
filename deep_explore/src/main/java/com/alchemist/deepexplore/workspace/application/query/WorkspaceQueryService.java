package com.alchemist.deepexplore.workspace.application.query;

import com.alchemist.deepexplore.workspace.application.WorkspaceNotFoundException;
import com.alchemist.deepexplore.workspace.config.WorkspaceProperties;
import com.alchemist.deepexplore.workspace.domain.Workspace;
import com.alchemist.deepexplore.workspace.domain.WorkspaceStatus;
import com.alchemist.deepexplore.workspace.port.SandboxRuntime;
import com.alchemist.deepexplore.workspace.port.WorkspaceStore;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceQueryService {

    private final WorkspaceStore store;
    private final SandboxRuntime runtime;
    private final WorkspaceProperties properties;

    public WorkspaceQueryService(
            WorkspaceStore store,
            SandboxRuntime runtime,
            WorkspaceProperties properties
    ) {
        this.store = store;
        this.runtime = runtime;
        this.properties = properties;
    }

    public List<Workspace> list() {
        return store.list(properties.ownerId()).stream()
                .map(this::reconcileRuntimeState)
                .toList();
    }

    public Workspace get(String workspaceId) {
        Workspace workspace = store.find(
                        workspaceId,
                        properties.ownerId()
                )
                .orElseThrow(() ->
                        new WorkspaceNotFoundException(workspaceId));
        return reconcileRuntimeState(workspace);
    }

    private Workspace reconcileRuntimeState(Workspace workspace) {
        SandboxRuntime.RuntimeState state = runtime.currentState(
                workspace.containerId()
        );
        WorkspaceStatus actualStatus = switch (state) {
            case RUNNING -> WorkspaceStatus.RUNNING;
            case STOPPED -> WorkspaceStatus.STOPPED;
            case ABSENT -> workspace.status() == WorkspaceStatus.ERROR
                    ? WorkspaceStatus.ERROR
                    : WorkspaceStatus.STOPPED;
            case UNAVAILABLE -> workspace.status();
        };
        String actualContainerId = state == SandboxRuntime.RuntimeState.ABSENT
                ? null
                : workspace.containerId();
        if (workspace.status() == actualStatus
                && Objects.equals(
                        workspace.containerId(),
                        actualContainerId
                )) {
            return workspace;
        }
        store.updateRuntime(
                workspace.id(),
                actualStatus,
                actualContainerId,
                actualStatus == WorkspaceStatus.ERROR
                        ? workspace.lastError()
                        : null
        );
        return store.find(workspace.id(), properties.ownerId()).orElseThrow();
    }
}
