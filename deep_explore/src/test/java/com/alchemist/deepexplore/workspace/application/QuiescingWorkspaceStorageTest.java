package com.alchemist.deepexplore.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Test
    void sharesOneSandboxPauseAcrossConcurrentReads() throws Exception {
        when(runtime.currentState(CONTAINER_ID))
                .thenReturn(SandboxRuntime.RuntimeState.RUNNING);
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        when(delegate.read(WORKSPACE_ID, "README.md")).thenAnswer(ignored -> {
            int current = active.incrementAndGet();
            maximum.accumulateAndGet(current, Math::max);
            entered.countDown();
            try {
                if (!release.await(5, TimeUnit.SECONDS)) {
                    throw new AssertionError("Concurrent reads did not overlap");
                }
                return file();
            } finally {
                active.decrementAndGet();
            }
        });

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(
                    () -> storage.read(WORKSPACE_ID, "README.md")
            );
            var second = executor.submit(
                    () -> storage.read(WORKSPACE_ID, "README.md")
            );

            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(maximum).hasValue(2);
            verify(runtime).pause(CONTAINER_ID);
            verify(runtime, never()).resume(CONTAINER_ID);

            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo(file());
            assertThat(second.get(5, TimeUnit.SECONDS)).isEqualTo(file());
        }

        verify(runtime).resume(CONTAINER_ID);
        verify(runtime).currentState(CONTAINER_ID);
        verify(delegate, times(2)).read(WORKSPACE_ID, "README.md");
    }

    @Test
    void mutationWaitsUntilActiveReadHasFinished() throws Exception {
        when(runtime.currentState(CONTAINER_ID))
                .thenReturn(SandboxRuntime.RuntimeState.RUNNING);
        CountDownLatch readEntered = new CountDownLatch(1);
        CountDownLatch releaseRead = new CountDownLatch(1);
        when(delegate.read(WORKSPACE_ID, "README.md")).thenAnswer(ignored -> {
            readEntered.countDown();
            if (!releaseRead.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting to release read");
            }
            return file();
        });
        when(delegate.write(
                WORKSPACE_ID,
                "README.md",
                "changed",
                "revision"
        )).thenReturn(file());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var read = executor.submit(
                    () -> storage.read(WORKSPACE_ID, "README.md")
            );
            assertThat(readEntered.await(5, TimeUnit.SECONDS)).isTrue();
            var write = executor.submit(() -> storage.write(
                    WORKSPACE_ID,
                    "README.md",
                    "changed",
                    "revision"
            ));

            Thread.sleep(100);
            verify(delegate, never()).write(
                    WORKSPACE_ID,
                    "README.md",
                    "changed",
                    "revision"
            );

            releaseRead.countDown();
            assertThat(read.get(5, TimeUnit.SECONDS)).isEqualTo(file());
            assertThat(write.get(5, TimeUnit.SECONDS)).isEqualTo(file());
        }

        verify(delegate).write(
                WORKSPACE_ID,
                "README.md",
                "changed",
                "revision"
        );
        verify(runtime, times(2)).pause(CONTAINER_ID);
        verify(runtime, times(2)).resume(CONTAINER_ID);
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
