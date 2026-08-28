package com.alchemist.deepexplore.workspace.application.lifecycle;

import com.alchemist.deepexplore.workspace.application.terminal.TerminalSessionRegistry;
import com.alchemist.deepexplore.workspace.application.WorkspaceChangeService;
import com.alchemist.deepexplore.workspace.config.WorkspaceProperties;
import com.alchemist.deepexplore.workspace.application.WorkspaceTemplateInitializer;
import com.alchemist.deepexplore.workspace.application.query.WorkspaceQueryService;
import com.alchemist.deepexplore.workspace.domain.RuntimeProfile;
import com.alchemist.deepexplore.workspace.domain.StarterTemplate;
import com.alchemist.deepexplore.workspace.domain.Workspace;
import com.alchemist.deepexplore.workspace.domain.WorkspaceStatus;
import com.alchemist.deepexplore.workspace.port.SandboxRuntime;
import com.alchemist.deepexplore.workspace.port.SandboxPreviewRuntime;
import com.alchemist.deepexplore.workspace.port.WorkspaceStorage;
import com.alchemist.deepexplore.workspace.port.WorkspaceStore;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceLifecycleService {

    private final WorkspaceStore store;
    private final WorkspaceStorage storage;
    private final SandboxRuntime runtime;
    private final SandboxPreviewRuntime previews;
    private final WorkspaceTemplateInitializer templates;
    private final WorkspaceChangeService changes;
    private final TerminalSessionRegistry terminalSessions;
    private final WorkspaceQueryService queries;
    private final WorkspaceOperationCoordinator operations;
    private final WorkspaceProperties properties;

    public WorkspaceLifecycleService(
            WorkspaceStore store,
            WorkspaceStorage storage,
            SandboxRuntime runtime,
            SandboxPreviewRuntime previews,
            WorkspaceTemplateInitializer templates,
            WorkspaceChangeService changes,
            TerminalSessionRegistry terminalSessions,
            WorkspaceQueryService queries,
            WorkspaceOperationCoordinator operations,
            WorkspaceProperties properties
    ) {
        this.store = store;
        this.storage = storage;
        this.runtime = runtime;
        this.previews = previews;
        this.templates = templates;
        this.changes = changes;
        this.terminalSessions = terminalSessions;
        this.queries = queries;
        this.operations = operations;
        this.properties = properties;
    }

    public Workspace create(String name, RuntimeProfile runtimeProfile) {
        return create(name, runtimeProfile, StarterTemplate.WEB_TYPESCRIPT);
    }

    public Workspace create(
            String name,
            RuntimeProfile runtimeProfile,
            StarterTemplate starterTemplate
    ) {
        String workspaceId = UUID.randomUUID().toString();
        String normalizedName = normalizeName(name);
        RuntimeProfile profile = runtimeProfile == null
                ? RuntimeProfile.FULLSTACK
                : runtimeProfile;
        StarterTemplate template = starterTemplate == null
                ? StarterTemplate.WEB_TYPESCRIPT
                : starterTemplate;
        try {
            templates.initializeIfEmpty(workspaceId, template);
            return store.create(
                    workspaceId,
                    properties.ownerId(),
                    normalizedName,
                    profile
            );
        } catch (RuntimeException error) {
            storage.deleteWorkspace(workspaceId);
            throw error;
        }
    }

    public Workspace start(String workspaceId) {
        return operations.withLock(workspaceId, () -> {
            Workspace workspace = queries.get(workspaceId);
            store.updateRuntime(
                    workspace.id(),
                    WorkspaceStatus.STARTING,
                    workspace.containerId(),
                    null
            );
            try {
                SandboxRuntime.RuntimeInstance instance = runtime.start(
                        workspace
                );
                store.updateRuntime(
                        workspace.id(),
                        WorkspaceStatus.RUNNING,
                        instance.containerId(),
                        null
                );
                return queries.get(workspace.id());
            } catch (RuntimeException error) {
                store.updateRuntime(
                        workspace.id(),
                        WorkspaceStatus.ERROR,
                        workspace.containerId(),
                        error.getMessage()
                );
                throw error;
            }
        });
    }

    public Workspace stop(String workspaceId) {
        return operations.withLock(workspaceId, () -> {
            Workspace workspace = queries.get(workspaceId);
            store.updateRuntime(
                    workspace.id(),
                    WorkspaceStatus.STOPPING,
                    workspace.containerId(),
                    null
            );
            try {
                terminalSessions.closeWorkspace(workspace.id());
                previews.stop(workspace.containerId());
                runtime.stop(workspace.containerId());
                store.updateRuntime(
                        workspace.id(),
                        WorkspaceStatus.STOPPED,
                        workspace.containerId(),
                        null
                );
                return queries.get(workspace.id());
            } catch (RuntimeException error) {
                store.updateRuntime(
                        workspace.id(),
                        WorkspaceStatus.ERROR,
                        workspace.containerId(),
                        error.getMessage()
                );
                throw error;
            }
        });
    }

    public void delete(String workspaceId) {
        operations.withLock(workspaceId, () -> {
            Workspace workspace = queries.get(workspaceId);
            terminalSessions.closeWorkspace(workspace.id());
            previews.stop(workspace.containerId());
            runtime.remove(workspace.containerId());
            changes.closeWorkspace(workspace.id());
            storage.deleteWorkspace(workspace.id());
            store.delete(workspace.id(), properties.ownerId());
            return null;
        });
        operations.forget(workspaceId);
    }

    private static String normalizeName(String name) {
        String normalized = name == null ? "" : name.strip();
        if (normalized.isEmpty()) {
            return "Untitled Workspace";
        }
        return normalized.substring(0, Math.min(80, normalized.length()));
    }
}
