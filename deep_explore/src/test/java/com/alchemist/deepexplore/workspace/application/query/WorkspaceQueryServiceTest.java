package com.alchemist.deepexplore.workspace.application.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alchemist.deepexplore.workspace.config.WorkspaceProperties;
import com.alchemist.deepexplore.workspace.domain.RuntimeProfile;
import com.alchemist.deepexplore.workspace.domain.Workspace;
import com.alchemist.deepexplore.workspace.domain.WorkspaceStatus;
import com.alchemist.deepexplore.workspace.port.SandboxRuntime;
import com.alchemist.deepexplore.workspace.port.WorkspaceStore;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WorkspaceQueryServiceTest {

    private static final String WORKSPACE_ID =
            "00000000-0000-0000-0000-000000000003";

    @Test
    void returnsExistingWorkspaceWithoutReinstallingTemplate() {
        WorkspaceStore store = mock(WorkspaceStore.class);
        SandboxRuntime runtime = mock(SandboxRuntime.class);
        Workspace existing = new Workspace(
                WORKSPACE_ID,
                "local-user",
                "Existing",
                RuntimeProfile.FULLSTACK,
                null,
                WorkspaceStatus.STOPPED,
                null,
                Instant.EPOCH,
                Instant.EPOCH,
                null
        );
        when(store.find(WORKSPACE_ID, "local-user"))
                .thenReturn(Optional.of(existing));
        when(runtime.currentState(null))
                .thenReturn(SandboxRuntime.RuntimeState.ABSENT);
        WorkspaceQueryService service = new WorkspaceQueryService(
                store,
                runtime,
                properties()
        );

        Workspace result = service.get(WORKSPACE_ID);

        assertThat(result).isEqualTo(existing);
    }

    private static WorkspaceProperties properties() {
        return new WorkspaceProperties(
                true,
                "local-user",
                ".deep-explore-data",
                Duration.ofSeconds(5),
                1024,
                1024,
                1024,
                1024,
                2048,
                20,
                new WorkspaceProperties.Docker(
                        "unix:///var/run/docker.sock",
                        "deep-explore/sandbox-java21:test",
                        256 * 1024 * 1024L,
                        1_000_000_000L,
                        64,
                        "none"
                )
        );
    }
}
