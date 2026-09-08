package com.alchemist.deepexplore.workspace.application;

import com.alchemist.deepexplore.workspace.application.lifecycle.WorkspaceOperationCoordinator;
import com.alchemist.deepexplore.workspace.application.query.WorkspaceQueryService;
import com.alchemist.deepexplore.workspace.domain.Workspace;
import com.alchemist.deepexplore.workspace.domain.WorkspaceEntry;
import com.alchemist.deepexplore.workspace.domain.WorkspaceFile;
import com.alchemist.deepexplore.workspace.domain.WorkspaceTreeNode;
import com.alchemist.deepexplore.workspace.port.SandboxRuntime;
import com.alchemist.deepexplore.workspace.port.WorkspaceStorage;
import java.io.InputStream;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

/**
 * Freezes sandbox writers while the host resolves and accesses workspace paths.
 */
@Service
@Primary
public class QuiescingWorkspaceStorage implements WorkspaceStorage {

    private final WorkspaceStorage delegate;
    private final WorkspaceQueryService workspaces;
    private final WorkspaceOperationCoordinator operations;
    private final SandboxRuntime runtime;

    public QuiescingWorkspaceStorage(
            @Qualifier("localWorkspaceStorage") WorkspaceStorage delegate,
            WorkspaceQueryService workspaces,
            WorkspaceOperationCoordinator operations,
            SandboxRuntime runtime
    ) {
        this.delegate = delegate;
        this.workspaces = workspaces;
        this.operations = operations;
        this.runtime = runtime;
    }

    @Override
    public void initialize(String workspaceId) {
        delegate.initialize(workspaceId);
    }

    @Override
    public void deleteWorkspace(String workspaceId) {
        delegate.deleteWorkspace(workspaceId);
    }

    @Override
    public List<WorkspaceEntry> list(String workspaceId, String path) {
        return access(workspaceId, () -> delegate.list(workspaceId, path));
    }

    @Override
    public List<WorkspaceTreeNode> tree(String workspaceId) {
        return access(workspaceId, () -> delegate.tree(workspaceId));
    }

    @Override
    public WorkspaceEntry create(
            String workspaceId,
            String parentPath,
            String name,
            WorkspaceEntry.Type type
    ) {
        return access(
                workspaceId,
                () -> delegate.create(workspaceId, parentPath, name, type)
        );
    }

    @Override
    public WorkspaceEntry move(
            String workspaceId,
            String sourcePath,
            String targetPath
    ) {
        return access(
                workspaceId,
                () -> delegate.move(workspaceId, sourcePath, targetPath)
        );
    }

    @Override
    public void deleteEntry(
            String workspaceId,
            String path,
            boolean recursive
    ) {
        access(workspaceId, () -> {
            delegate.deleteEntry(workspaceId, path, recursive);
            return null;
        });
    }

    @Override
    public WorkspaceFile read(String workspaceId, String path) {
        return access(workspaceId, () -> delegate.read(workspaceId, path));
    }

    @Override
    public WorkspaceFile write(
            String workspaceId,
            String path,
            String content,
            String expectedRevision
    ) {
        return access(
                workspaceId,
                () -> delegate.write(
                        workspaceId,
                        path,
                        content,
                        expectedRevision
                )
        );
    }

    @Override
    public UploadResult upload(
            String workspaceId,
            String path,
            InputStream content,
            long declaredSize
    ) {
        return access(
                workspaceId,
                () -> delegate.upload(
                        workspaceId,
                        path,
                        content,
                        declaredSize
                )
        );
    }

    @Override
    public ImportResult importZip(
            String workspaceId,
            InputStream content,
            long declaredSize
    ) {
        return access(
                workspaceId,
                () -> delegate.importZip(workspaceId, content, declaredSize)
        );
    }

    @Override
    public String containerWorkingDirectory(
            String workspaceId,
            String relativePath
    ) {
        return access(
                workspaceId,
                () -> delegate.containerWorkingDirectory(
                        workspaceId,
                        relativePath
                )
        );
    }

    private <T> T access(String workspaceId, Supplier<T> action) {
        return operations.withLock(workspaceId, () -> {
            Workspace workspace = workspaces.get(workspaceId);
            String containerId = workspace.containerId();
            if (containerId == null || containerId.isBlank()) {
                return action.get();
            }

            SandboxRuntime.RuntimeState state = runtime.currentState(containerId);
            if (state == SandboxRuntime.RuntimeState.UNAVAILABLE) {
                throw new WorkspaceOperationException(
                        "DOCKER_UNAVAILABLE",
                        "Workspace files cannot be accessed safely while "
                                + "the sandbox state is unavailable"
                );
            }
            if (state != SandboxRuntime.RuntimeState.RUNNING) {
                return action.get();
            }

            runtime.pause(containerId);
            Throwable failure = null;
            try {
                return action.get();
            } catch (RuntimeException | Error error) {
                failure = error;
                throw error;
            } finally {
                try {
                    runtime.resume(containerId);
                } catch (RuntimeException resumeError) {
                    if (failure != null) {
                        failure.addSuppressed(resumeError);
                    } else {
                        throw resumeError;
                    }
                }
            }
        });
    }
}
