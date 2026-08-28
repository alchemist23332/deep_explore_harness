package com.alchemist.deepexplore.workspace.application.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alchemist.deepexplore.workspace.application.WorkspaceOperationException;
import com.alchemist.deepexplore.workspace.application.lifecycle.WorkspaceLifecycleService;
import com.alchemist.deepexplore.workspace.application.lifecycle.WorkspaceOperationCoordinator;
import com.alchemist.deepexplore.workspace.application.query.WorkspaceQueryService;
import com.alchemist.deepexplore.workspace.config.PreviewProperties;
import com.alchemist.deepexplore.workspace.domain.RuntimeProfile;
import com.alchemist.deepexplore.workspace.domain.Workspace;
import com.alchemist.deepexplore.workspace.domain.WorkspaceStatus;
import com.alchemist.deepexplore.workspace.port.PreviewProbe;
import com.alchemist.deepexplore.workspace.port.SandboxPreviewRuntime;
import com.alchemist.deepexplore.workspace.port.WorkspaceStorage;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PreviewApplicationServiceTest {

    private static final String WORKSPACE_ID = "workspace-1";
    private static final String CONTAINER_ID = "container-1";

    private WorkspaceQueryService workspaces;
    private WorkspaceLifecycleService lifecycle;
    private WorkspaceStorage storage;
    private SandboxPreviewRuntime runtime;
    private PreviewProbe probe;
    private PreviewApplicationService service;

    @BeforeEach
    void setUp() {
        workspaces = mock(WorkspaceQueryService.class);
        lifecycle = mock(WorkspaceLifecycleService.class);
        storage = mock(WorkspaceStorage.class);
        runtime = mock(SandboxPreviewRuntime.class);
        probe = mock(PreviewProbe.class);
        when(workspaces.get(WORKSPACE_ID)).thenReturn(workspace(
                WorkspaceStatus.RUNNING
        ));
        when(storage.containerWorkingDirectory(WORKSPACE_ID, ""))
                .thenReturn("/workspace");
        when(runtime.address(CONTAINER_ID)).thenReturn(address());
        service = service(Duration.ofSeconds(1));
    }

    @Test
    void startsPreviewAndReturnsHealthyUrl() {
        when(runtime.state(CONTAINER_ID))
                .thenReturn(SandboxPreviewRuntime.ProcessState.RUNNING);
        when(probe.isHealthy(
                "http://127.0.0.1:49152/",
                Duration.ofMillis(100)
        )).thenReturn(true);
        when(runtime.logs(CONTAINER_ID, 32_768))
                .thenReturn("ready");

        PreviewView preview = service.start(
                WORKSPACE_ID,
                "pnpm dev --host 0.0.0.0 --port 3000",
                "",
                "/"
        );

        assertThat(preview.status()).isEqualTo(PreviewStatus.RUNNING);
        assertThat(preview.url()).isEqualTo("http://127.0.0.1:49152");
        assertThat(preview.logs()).isEqualTo("ready");
        verify(runtime).start(
                CONTAINER_ID,
                "pnpm dev --host 0.0.0.0 --port 3000",
                "/workspace"
        );
    }

    @Test
    void failsWhenPreviewProcessExitsBeforeHealthCheck() {
        when(runtime.state(CONTAINER_ID))
                .thenReturn(SandboxPreviewRuntime.ProcessState.FAILED);
        when(runtime.logs(CONTAINER_ID, 32_768))
                .thenReturn("vite failed");

        assertThatThrownBy(() -> service.start(
                WORKSPACE_ID,
                "pnpm dev",
                "",
                "/"
        ))
                .isInstanceOf(WorkspaceOperationException.class)
                .hasMessageContaining("vite failed");
    }

    @Test
    void stopsPreviewAfterStartupTimeout() {
        service = service(Duration.ofMillis(1));
        when(runtime.state(CONTAINER_ID))
                .thenReturn(SandboxPreviewRuntime.ProcessState.RUNNING);
        when(probe.isHealthy(
                "http://127.0.0.1:49152/",
                Duration.ofMillis(100)
        )).thenReturn(false);

        assertThatThrownBy(() -> service.start(
                WORKSPACE_ID,
                "pnpm dev",
                "",
                "/"
        ))
                .isInstanceOf(WorkspaceOperationException.class)
                .extracting(error ->
                        ((WorkspaceOperationException) error).code())
                .isEqualTo("PREVIEW_START_TIMEOUT");
        verify(runtime).stop(CONTAINER_ID);
    }

    @Test
    void reportsRunningAndStoppedStates() {
        when(runtime.state(CONTAINER_ID))
                .thenReturn(SandboxPreviewRuntime.ProcessState.RUNNING);
        when(probe.isHealthy(
                "http://127.0.0.1:49152",
                Duration.ofMillis(100)
        )).thenReturn(true);

        assertThat(service.status(WORKSPACE_ID).status())
                .isEqualTo(PreviewStatus.RUNNING);

        when(workspaces.get(WORKSPACE_ID)).thenReturn(workspace(
                WorkspaceStatus.STOPPED
        ));

        assertThat(service.status(WORKSPACE_ID).status())
                .isEqualTo(PreviewStatus.STOPPED);
    }

    @Test
    void rejectsExternalHealthUrl() {
        assertThatThrownBy(() -> service.start(
                WORKSPACE_ID,
                "pnpm dev",
                "",
                "https://example.com"
        ))
                .isInstanceOf(WorkspaceOperationException.class)
                .extracting(error ->
                        ((WorkspaceOperationException) error).code())
                .isEqualTo("INVALID_PREVIEW_HEALTH_PATH");
    }

    private PreviewApplicationService service(Duration startupTimeout) {
        return new PreviewApplicationService(
                workspaces,
                lifecycle,
                storage,
                runtime,
                probe,
                new WorkspaceOperationCoordinator(),
                new PreviewProperties(
                        true,
                        3000,
                        "127.0.0.1",
                        startupTimeout,
                        Duration.ofMillis(100),
                        32_768
                )
        );
    }

    private static SandboxPreviewRuntime.PreviewAddress address() {
        return new SandboxPreviewRuntime.PreviewAddress(
                "127.0.0.1",
                49_152,
                3_000
        );
    }

    private static Workspace workspace(WorkspaceStatus status) {
        return new Workspace(
                WORKSPACE_ID,
                "local-user",
                "Workspace",
                RuntimeProfile.FULLSTACK,
                CONTAINER_ID,
                status,
                null,
                Instant.EPOCH,
                Instant.EPOCH,
                Instant.EPOCH
        );
    }
}
