package com.alchemist.deepexplore.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alchemist.deepexplore.workspace.application.lifecycle.WorkspaceOperationCoordinator;
import com.alchemist.deepexplore.workspace.application.query.WorkspaceQueryService;
import com.alchemist.deepexplore.workspace.domain.RuntimeProfile;
import com.alchemist.deepexplore.workspace.domain.Workspace;
import com.alchemist.deepexplore.workspace.domain.WorkspaceFile;
import com.alchemist.deepexplore.workspace.domain.WorkspaceStatus;
import com.alchemist.deepexplore.workspace.port.SandboxRuntime;
import com.alchemist.deepexplore.workspace.port.WorkspaceStorage;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class QuiescingWorkspaceStorageTest {

    private static final String WORKSPACE_ID = "workspace-1";
    private static final String CONTAINER_ID = "container-1";

    private final WorkspaceStorage delegate = mock(WorkspaceStorage.class);
    private final WorkspaceQueryService workspaces =
            mock(WorkspaceQueryService.class);
    private final SandboxRuntime runtime = mock(SandboxRuntime.class);
    private QuiescingWorkspaceStorage storage;

    @BeforeEach
    void setUp() {
        when(workspaces.get(WORKSPACE_ID)).thenReturn(workspace(CONTAINER_ID));
        storage = new QuiescingWorkspaceStorage(
                delegate,
                workspaces,
                new WorkspaceOperationCoordinator(),
                runtime
        );
    }

    @Test
    void pausesRunningSandboxAroundHostFileAccess() {
        WorkspaceFile expected = file();
        when(runtime.currentState(CONTAINER_ID))
                .thenReturn(SandboxRuntime.RuntimeState.RUNNING);
        when(delegate.read(WORKSPACE_ID, "README.md")).thenReturn(expected);

        WorkspaceFile actual = storage.read(WORKSPACE_ID, "README.md");

        assertThat(actual).isEqualTo(expected);
        InOrder order = inOrder(runtime, delegate);
        order.verify(runtime).currentState(CONTAINER_ID);
        order.verify(runtime).pause(CONTAINER_ID);
        order.verify(delegate).read(WORKSPACE_ID, "README.md");
        order.verify(runtime).resume(CONTAINER_ID);
    }

    @Test
    void resumesSandboxWhenFileAccessFails() {
        when(runtime.currentState(CONTAINER_ID))
                .thenReturn(SandboxRuntime.RuntimeState.RUNNING);
        when(delegate.read(WORKSPACE_ID, "README.md"))
                .thenThrow(new WorkspaceOperationException(
                        "WORKSPACE_FILE_OPERATION_FAILED",
                        "read failed"
                ));

        assertThatThrownBy(() -> storage.read(WORKSPACE_ID, "README.md"))
                .isInstanceOf(WorkspaceOperationException.class)
                .hasMessage("read failed");

        verify(runtime).pause(CONTAINER_ID);
        verify(runtime).resume(CONTAINER_ID);
    }

    @Test
    void failsClosedWhenSandboxStateIsUnavailable() {
        when(runtime.currentState(CONTAINER_ID))
                .thenReturn(SandboxRuntime.RuntimeState.UNAVAILABLE);

        assertThatThrownBy(() -> storage.read(WORKSPACE_ID, "README.md"))
                .isInstanceOf(WorkspaceOperationException.class)
                .extracting(error ->
                        ((WorkspaceOperationException) error).code())
                .isEqualTo("DOCKER_UNAVAILABLE");

        verify(delegate, never()).read(WORKSPACE_ID, "README.md");
        verify(runtime, never()).pause(CONTAINER_ID);
    }

    @Test
    void accessesFilesDirectlyWhenWorkspaceHasNoContainer() {
        WorkspaceFile expected = file();
        when(workspaces.get(WORKSPACE_ID)).thenReturn(workspace(null));
        when(delegate.read(WORKSPACE_ID, "README.md")).thenReturn(expected);

        assertThat(storage.read(WORKSPACE_ID, "README.md"))
                .isEqualTo(expected);

        verify(runtime, never()).currentState(CONTAINER_ID);
        verify(delegate).read(WORKSPACE_ID, "README.md");
    }

    private static Workspace workspace(String containerId) {
        return new Workspace(
                WORKSPACE_ID,
                "local-user",
                "Workspace",
                RuntimeProfile.FULLSTACK,
                containerId,
                containerId == null
                        ? WorkspaceStatus.STOPPED
                        : WorkspaceStatus.RUNNING,
                null,
                Instant.EPOCH,
                Instant.EPOCH,
                null
        );
    }

    private static WorkspaceFile file() {
        return new WorkspaceFile(
                "README.md",
                "content",
                7,
                Instant.EPOCH,
                "revision"
        );
    }
}
