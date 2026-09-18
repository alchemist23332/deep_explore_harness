package com.alchemist.deepexplore.workspace.application.terminal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alchemist.deepexplore.workspace.application.lifecycle.WorkspaceOperationCoordinator;
import com.alchemist.deepexplore.workspace.application.query.WorkspaceQueryService;
import com.alchemist.deepexplore.workspace.domain.RuntimeProfile;
import com.alchemist.deepexplore.workspace.domain.Workspace;
import com.alchemist.deepexplore.workspace.domain.WorkspaceStatus;
import com.alchemist.deepexplore.workspace.port.SandboxTerminal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

class TerminalApplicationServiceTest {

    private static final String WORKSPACE_ID = "workspace-1";
    private static final String CONTAINER_ID = "container-1";

    @Test
    void terminalInputWaitsForWorkspaceReaders() throws Exception {
        WorkspaceQueryService workspaces = mock(WorkspaceQueryService.class);
        TerminalSessionRegistry sessions = mock(TerminalSessionRegistry.class);
        SandboxTerminal.Session session = mock(SandboxTerminal.Session.class);
        WorkspaceOperationCoordinator operations =
                new WorkspaceOperationCoordinator();
        when(workspaces.get(WORKSPACE_ID)).thenReturn(workspace());
        when(sessions.open(WORKSPACE_ID, CONTAINER_ID, 100, 30))
                .thenReturn(session);
        when(session.id()).thenReturn("terminal-1");
        when(session.output()).thenReturn(Flux.empty());
        TerminalApplicationService service = new TerminalApplicationService(
                workspaces,
                sessions,
                operations
        );
        TerminalConnection connection = service.open(WORKSPACE_ID, 100, 30);
        CountDownLatch readerEntered = new CountDownLatch(1);
        CountDownLatch releaseReader = new CountDownLatch(1);
        CountDownLatch writerStarted = new CountDownLatch(1);
        byte[] input = "touch changed.txt\n".getBytes(StandardCharsets.UTF_8);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var reader = executor.submit(() -> operations.withReadLock(
                    WORKSPACE_ID,
                    () -> {
                        readerEntered.countDown();
                        await(releaseReader);
                        return true;
                    }
            ));
            assertThat(readerEntered.await(5, TimeUnit.SECONDS)).isTrue();

            var write = executor.submit(() -> {
                writerStarted.countDown();
                connection.input(input);
                return true;
            });
            assertThat(writerStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Thread.sleep(100);
            verify(session, never()).input(input);

            releaseReader.countDown();
            assertThat(reader.get(5, TimeUnit.SECONDS)).isTrue();
            assertThat(write.get(5, TimeUnit.SECONDS)).isTrue();
        }

        verify(session).input(input);
    }

    private static Workspace workspace() {
        return new Workspace(
                WORKSPACE_ID,
                "local-user",
                "Workspace",
                RuntimeProfile.FULLSTACK,
                CONTAINER_ID,
                WorkspaceStatus.RUNNING,
                null,
                Instant.EPOCH,
                Instant.EPOCH,
                null
        );
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for test latch");
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Test was interrupted", error);
        }
    }
}
