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
import java.util.concurrent.ConcurrentHashMap;
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
    private final ConcurrentHashMap<String, QuiescenceState> quiescence =
            new ConcurrentHashMap<>();

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
        operations.withWriteLock(workspaceId, () -> {
            delegate.initialize(workspaceId);
            return null;
        });
    }

    @Override
    public void deleteWorkspace(String workspaceId) {
        operations.withWriteLock(workspaceId, () -> {
            delegate.deleteWorkspace(workspaceId);
            quiescence.remove(workspaceId);
            return null;
        });
    }

    @Override
    public List<WorkspaceEntry> list(String workspaceId, String path) {
        return readAccess(
                workspaceId,
                () -> delegate.list(workspaceId, path)
        );
    }

    @Override
    public List<WorkspaceTreeNode> tree(String workspaceId) {
        return readAccess(workspaceId, () -> delegate.tree(workspaceId));
    }

    @Override
    public WorkspaceEntry create(
            String workspaceId,
            String parentPath,
            String name,
            WorkspaceEntry.Type type
    ) {
        return writeAccess(
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
        return writeAccess(
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
        writeAccess(workspaceId, () -> {
            delegate.deleteEntry(workspaceId, path, recursive);
            return null;
        });
    }

    @Override
    public WorkspaceFile read(String workspaceId, String path) {
        return readAccess(
                workspaceId,
                () -> delegate.read(workspaceId, path)
        );
    }

    @Override
    public WorkspaceFile write(
            String workspaceId,
            String path,
            String content,
            String expectedRevision
    ) {
        return writeAccess(
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
        return writeAccess(
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
        return writeAccess(
                workspaceId,
                () -> delegate.importZip(workspaceId, content, declaredSize)
        );
    }

    @Override
    public String containerWorkingDirectory(
            String workspaceId,
            String relativePath
    ) {
        return readAccess(
                workspaceId,
                () -> delegate.containerWorkingDirectory(
                        workspaceId,
                        relativePath
                )
        );
    }

    private <T> T readAccess(String workspaceId, Supplier<T> action) {
        return operations.withReadLock(
                workspaceId,
                () -> quiescedAccess(workspaceId, action)
        );
    }

    private <T> T writeAccess(String workspaceId, Supplier<T> action) {
        return operations.withWriteLock(
                workspaceId,
                () -> quiescedAccess(workspaceId, action)
        );
    }

    private <T> T quiescedAccess(String workspaceId, Supplier<T> action) {
        Workspace workspace = workspaces.get(workspaceId);
        String containerId = workspace.containerId();
        if (containerId == null || containerId.isBlank()) {
            return action.get();
        }

        QuiescenceState state = quiescence.computeIfAbsent(
                workspaceId,
                ignored -> new QuiescenceState()
        );
        enterQuiescence(state, containerId);
        Throwable failure = null;
        try {
            return action.get();
        } catch (RuntimeException | Error error) {
            failure = error;
            throw error;
        } finally {
            leaveQuiescence(state, failure);
        }
    }

    private void enterQuiescence(
            QuiescenceState quiescenceState,
            String containerId
    ) {
        synchronized (quiescenceState) {
            if (quiescenceState.users > 0) {
                if (!containerId.equals(quiescenceState.containerId)) {
                    throw new WorkspaceOperationException(
                            "WORKSPACE_RUNTIME_CHANGED",
                            "Workspace runtime changed during file access"
                    );
                }
                quiescenceState.users++;
                return;
            }

            SandboxRuntime.RuntimeState runtimeState =
                    runtime.currentState(containerId);
            if (runtimeState == SandboxRuntime.RuntimeState.UNAVAILABLE) {
                throw new WorkspaceOperationException(
                        "DOCKER_UNAVAILABLE",
                        "Workspace files cannot be accessed safely while "
                                + "the sandbox state is unavailable"
                );
            }
            boolean resumeWhenIdle =
                    runtimeState == SandboxRuntime.RuntimeState.RUNNING;
            if (resumeWhenIdle) {
                runtime.pause(containerId);
            }
            quiescenceState.containerId = containerId;
            quiescenceState.resumeWhenIdle = resumeWhenIdle;
            quiescenceState.users = 1;
        }
    }

    private void leaveQuiescence(
            QuiescenceState quiescenceState,
            Throwable failure
    ) {
        synchronized (quiescenceState) {
            quiescenceState.users--;
            if (quiescenceState.users > 0) {
                return;
            }
            String containerId = quiescenceState.containerId;
            boolean resume = quiescenceState.resumeWhenIdle;
            quiescenceState.containerId = null;
            quiescenceState.resumeWhenIdle = false;
            if (!resume) {
                return;
            }
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
    }

    private static final class QuiescenceState {

        private int users;
        private String containerId;
        private boolean resumeWhenIdle;
    }
}
