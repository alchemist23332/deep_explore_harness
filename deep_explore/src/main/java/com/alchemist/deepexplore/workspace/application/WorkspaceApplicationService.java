package com.alchemist.deepexplore.workspace.application;

import com.alchemist.deepexplore.workspace.domain.CommandResult;
import com.alchemist.deepexplore.workspace.domain.RuntimeProfile;
import com.alchemist.deepexplore.workspace.domain.Workspace;
import com.alchemist.deepexplore.workspace.domain.WorkspaceStatus;
import com.alchemist.deepexplore.workspace.port.SandboxRuntime;
import com.alchemist.deepexplore.workspace.port.WorkspaceStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceApplicationService {

    private final WorkspaceStore store;
    private final SandboxRuntime runtime;
    private final WorkspaceDirectoryManager directories;
    private final WorkspaceTemplateInitializer templates;
    private final WorkspaceChangeService changes;
    private final TerminalSessionRegistry terminalSessions;
    private final WorkspaceProperties properties;
    private final ConcurrentHashMap<String, ReentrantLock> locks =
            new ConcurrentHashMap<>();

    public WorkspaceApplicationService(
            WorkspaceStore store,
            SandboxRuntime runtime,
            WorkspaceDirectoryManager directories,
            WorkspaceTemplateInitializer templates,
            WorkspaceChangeService changes,
            TerminalSessionRegistry terminalSessions,
            WorkspaceProperties properties
    ) {
        this.store = store;
        this.runtime = runtime;
        this.directories = directories;
        this.templates = templates;
        this.changes = changes;
        this.terminalSessions = terminalSessions;
        this.properties = properties;
    }

    public List<Workspace> list() {
        return store.list(properties.ownerId()).stream()
                .map(this::reconcileRuntimeState)
                .toList();
    }

    public Workspace get(String workspaceId) {
        Workspace workspace = store.find(workspaceId, properties.ownerId())
                .orElseThrow(() -> new WorkspaceNotFoundException(workspaceId));
        templates.initializeIfEmpty(
                workspace.id(),
                workspace.runtimeProfile()
        );
        return reconcileRuntimeState(workspace);
    }

    public Workspace create(String name, RuntimeProfile runtimeProfile) {
        String workspaceId = UUID.randomUUID().toString();
        String normalizedName = normalizeName(name);
        RuntimeProfile profile = runtimeProfile == null
                ? RuntimeProfile.JAVA_21
                : runtimeProfile;
        try {
            templates.initializeIfEmpty(workspaceId, profile);
            return store.create(
                    workspaceId,
                    properties.ownerId(),
                    normalizedName,
                    profile
            );
        } catch (RuntimeException error) {
            directories.delete(workspaceId);
            throw error;
        }
    }

    public Workspace start(String workspaceId) {
        return withLock(workspaceId, () -> {
            Workspace workspace = get(workspaceId);
            store.updateRuntime(
                    workspace.id(),
                    WorkspaceStatus.STARTING,
                    workspace.containerId(),
                    null
            );
            try {
                SandboxRuntime.RuntimeInstance instance = runtime.start(
                        workspace,
                        directories.filesDirectory(workspace.id()),
                        directories.mavenCacheDirectory(workspace.id())
                );
                store.updateRuntime(
                        workspace.id(),
                        WorkspaceStatus.RUNNING,
                        instance.containerId(),
                        null
                );
                return get(workspace.id());
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
        return withLock(workspaceId, () -> {
            Workspace workspace = get(workspaceId);
            store.updateRuntime(
                    workspace.id(),
                    WorkspaceStatus.STOPPING,
                    workspace.containerId(),
                    null
            );
            try {
                terminalSessions.closeWorkspace(workspace.id());
                runtime.stop(workspace.containerId());
                store.updateRuntime(
                        workspace.id(),
                        WorkspaceStatus.STOPPED,
                        workspace.containerId(),
                        null
                );
                return get(workspace.id());
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

    public CommandResult execute(
            String workspaceId,
            String command,
            String relativeWorkingDirectory
    ) {
        return withLock(workspaceId, () -> {
            Workspace workspace = get(workspaceId);
            if (workspace.status() != WorkspaceStatus.RUNNING) {
                throw new WorkspaceOperationException(
                        "SANDBOX_NOT_RUNNING",
                        "Start the workspace sandbox before running commands"
                );
            }
            String workingDirectory = containerWorkingDirectory(
                    workspaceId,
                    relativeWorkingDirectory
            );
            return runtime.execute(
                    workspace.containerId(),
                    command,
                    workingDirectory
            );
        });
    }

    public void delete(String workspaceId) {
        withLock(workspaceId, () -> {
            Workspace workspace = get(workspaceId);
            terminalSessions.closeWorkspace(workspace.id());
            runtime.remove(workspace.containerId());
            changes.closeWorkspace(workspace.id());
            directories.delete(workspace.id());
            store.delete(workspace.id(), properties.ownerId());
            return null;
        });
        locks.remove(workspaceId);
    }

    public RuntimeOverview runtimeOverview() {
        SandboxRuntime.Availability availability = runtime.availability();
        List<RuntimeProfileView> profiles = Arrays.stream(
                        RuntimeProfile.values()
                )
                .map(profile -> new RuntimeProfileView(
                        profile.name(),
                        profile.displayName(),
                        profile.description()
                ))
                .toList();
        return new RuntimeOverview(
                properties.enabled(),
                availability.available(),
                availability.message(),
                profiles
        );
    }

    private String containerWorkingDirectory(
            String workspaceId,
            String relativePath
    ) {
        Path directory = directories.resolveFile(
                workspaceId,
                relativePath
        );
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new WorkspaceOperationException(
                    "WORKSPACE_DIRECTORY_NOT_FOUND",
                    "Command working directory does not exist"
            );
        }
        String relative = directories.relativePath(workspaceId, directory);
        return relative.isBlank() ? "/workspace" : "/workspace/" + relative;
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
                && java.util.Objects.equals(
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

    private <T> T withLock(String workspaceId, java.util.function.Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(
                workspaceId,
                ignored -> new ReentrantLock()
        );
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    private static String normalizeName(String name) {
        String normalized = name == null ? "" : name.strip();
        if (normalized.isEmpty()) {
            return "Untitled Workspace";
        }
        return normalized.substring(0, Math.min(80, normalized.length()));
    }

    public record RuntimeOverview(
            boolean enabled,
            boolean available,
            String message,
            List<RuntimeProfileView> profiles
    ) {
    }

    public record RuntimeProfileView(
            String id,
            String displayName,
            String description
    ) {
    }
}
