package com.alchemist.deepexplore.workspace.application.preview;

import com.alchemist.deepexplore.workspace.application.WorkspaceOperationException;
import com.alchemist.deepexplore.workspace.application.lifecycle.WorkspaceLifecycleService;
import com.alchemist.deepexplore.workspace.application.lifecycle.WorkspaceOperationCoordinator;
import com.alchemist.deepexplore.workspace.application.query.WorkspaceQueryService;
import com.alchemist.deepexplore.workspace.config.PreviewProperties;
import com.alchemist.deepexplore.workspace.domain.Workspace;
import com.alchemist.deepexplore.workspace.domain.WorkspaceStatus;
import com.alchemist.deepexplore.workspace.port.PreviewProbe;
import com.alchemist.deepexplore.workspace.port.SandboxPreviewRuntime;
import com.alchemist.deepexplore.workspace.port.WorkspaceStorage;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class PreviewApplicationService {

    private static final int MAX_COMMAND_CHARACTERS = 8_000;

    private final WorkspaceQueryService workspaces;
    private final WorkspaceLifecycleService lifecycle;
    private final WorkspaceStorage storage;
    private final SandboxPreviewRuntime runtime;
    private final PreviewProbe probe;
    private final WorkspaceOperationCoordinator operations;
    private final PreviewProperties properties;

    public PreviewApplicationService(
            WorkspaceQueryService workspaces,
            WorkspaceLifecycleService lifecycle,
            WorkspaceStorage storage,
            SandboxPreviewRuntime runtime,
            PreviewProbe probe,
            WorkspaceOperationCoordinator operations,
            PreviewProperties properties
    ) {
        this.workspaces = workspaces;
        this.lifecycle = lifecycle;
        this.storage = storage;
        this.runtime = runtime;
        this.probe = probe;
        this.operations = operations;
        this.properties = properties;
    }

    public PreviewView start(
            String workspaceId,
            String command,
            String workingDirectory,
            String healthPath
    ) {
        requireEnabled();
        validateCommand(command);
        String path = normalizeHealthPath(healthPath);
        return operations.withLock(workspaceId, () -> {
            Workspace workspace = ensurePreviewReadyWorkspace(workspaceId);
            String containerDirectory = storage.containerWorkingDirectory(
                    workspaceId,
                    workingDirectory
            );
            runtime.start(
                    workspace.containerId(),
                    command,
                    containerDirectory
            );
            SandboxPreviewRuntime.PreviewAddress address = runtime.address(
                    workspace.containerId()
            );
            String healthUrl = address.url() + path;
            Instant deadline = Instant.now().plus(
                    properties.startupTimeout()
            );
            while (Instant.now().isBefore(deadline)) {
                SandboxPreviewRuntime.ProcessState state = runtime.state(
                        workspace.containerId()
                );
                if (state == SandboxPreviewRuntime.ProcessState.FAILED
                        || state == SandboxPreviewRuntime.ProcessState.SUCCEEDED
                        || state == SandboxPreviewRuntime.ProcessState.STOPPED) {
                    throw startFailed(workspace.containerId());
                }
                if (probe.isHealthy(
                        healthUrl,
                        properties.healthTimeout()
                )) {
                    return view(
                            PreviewStatus.RUNNING,
                            address,
                            runtime.logs(
                                    workspace.containerId(),
                                    properties.maxLogCharacters()
                            )
                    );
                }
                pause();
            }
            String logs = runtime.logs(
                    workspace.containerId(),
                    properties.maxLogCharacters()
            );
            runtime.stop(workspace.containerId());
            throw new WorkspaceOperationException(
                    "PREVIEW_START_TIMEOUT",
                    "Preview did not become healthy within "
                            + properties.startupTimeout().toSeconds()
                            + " seconds"
                            + logSuffix(logs)
            );
        });
    }

    public PreviewView status(String workspaceId) {
        Workspace workspace = workspaces.get(workspaceId);
        if (workspace.status() != WorkspaceStatus.RUNNING
                || workspace.containerId() == null) {
            return stopped();
        }
        SandboxPreviewRuntime.ProcessState state = runtime.state(
                workspace.containerId()
        );
        if (state == SandboxPreviewRuntime.ProcessState.STOPPED) {
            return stopped();
        }
        SandboxPreviewRuntime.PreviewAddress address = runtime.address(
                workspace.containerId()
        );
        String logs = runtime.logs(
                workspace.containerId(),
                properties.maxLogCharacters()
        );
        if (state != SandboxPreviewRuntime.ProcessState.RUNNING) {
            return view(PreviewStatus.FAILED, address, logs);
        }
        PreviewStatus status = probe.isHealthy(
                address.url(),
                properties.healthTimeout()
        )
                ? PreviewStatus.RUNNING
                : PreviewStatus.STARTING;
        return view(status, address, logs);
    }

    public PreviewView stop(String workspaceId) {
        return operations.withLock(workspaceId, () -> {
            Workspace workspace = workspaces.get(workspaceId);
            runtime.stop(workspace.containerId());
            return stopped();
        });
    }

    public String logs(String workspaceId) {
        Workspace workspace = workspaces.get(workspaceId);
        return runtime.logs(
                workspace.containerId(),
                properties.maxLogCharacters()
        );
    }

    private Workspace ensurePreviewReadyWorkspace(String workspaceId) {
        Workspace workspace = workspaces.get(workspaceId);
        if (workspace.status() != WorkspaceStatus.RUNNING) {
            return lifecycle.start(workspaceId);
        }
        try {
            runtime.address(workspace.containerId());
            return workspace;
        } catch (WorkspaceOperationException error) {
            if (!"PREVIEW_PORT_UNAVAILABLE".equals(error.code())) {
                throw error;
            }
            lifecycle.stop(workspaceId);
            return lifecycle.start(workspaceId);
        }
    }

    private WorkspaceOperationException startFailed(String containerId) {
        String logs = runtime.logs(
                containerId,
                properties.maxLogCharacters()
        );
        return new WorkspaceOperationException(
                "PREVIEW_START_FAILED",
                "Preview process exited before becoming healthy"
                        + logSuffix(logs)
        );
    }

    private PreviewView stopped() {
        return new PreviewView(
                PreviewStatus.STOPPED,
                null,
                properties.containerPort(),
                null,
                ""
        );
    }

    private static PreviewView view(
            PreviewStatus status,
            SandboxPreviewRuntime.PreviewAddress address,
            String logs
    ) {
        return new PreviewView(
                status,
                address.url(),
                address.containerPort(),
                address.hostPort(),
                logs
        );
    }

    private void requireEnabled() {
        if (!properties.enabled()) {
            throw new WorkspaceOperationException(
                    "PREVIEW_DISABLED",
                    "Workspace preview is disabled"
            );
        }
    }

    private static void validateCommand(String command) {
        if (command == null || command.isBlank()) {
            throw new WorkspaceOperationException(
                    "INVALID_PREVIEW_COMMAND",
                    "Preview command must not be blank"
            );
        }
        if (command.length() > MAX_COMMAND_CHARACTERS) {
            throw new WorkspaceOperationException(
                    "PREVIEW_COMMAND_TOO_LONG",
                    "Preview command exceeds the size limit"
            );
        }
    }

    private static String normalizeHealthPath(String healthPath) {
        String path = healthPath == null || healthPath.isBlank()
                ? "/"
                : healthPath.strip();
        if (!path.startsWith("/")
                || path.startsWith("//")
                || path.contains("://")) {
            throw new WorkspaceOperationException(
                    "INVALID_PREVIEW_HEALTH_PATH",
                    "Preview health path must be an absolute URL path"
            );
        }
        return path;
    }

    private static String logSuffix(String logs) {
        return logs == null || logs.isBlank()
                ? ""
                : ". Logs: " + logs.strip();
    }

    private static void pause() {
        try {
            Thread.sleep(250);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new WorkspaceOperationException(
                    "PREVIEW_START_INTERRUPTED",
                    "Preview startup was interrupted",
                    error
            );
        }
    }
}
