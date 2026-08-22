package com.alchemist.deepexplore.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alchemist.deepexplore.workspace.domain.RuntimeProfile;
import com.alchemist.deepexplore.workspace.domain.Workspace;
import com.alchemist.deepexplore.workspace.domain.WorkspaceStatus;
import com.alchemist.deepexplore.workspace.port.SandboxRuntime;
import com.alchemist.deepexplore.workspace.port.WorkspaceStore;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkspaceApplicationServiceTest {

    private static final String OWNER_ID = "local-user";
    private static final String WORKSPACE_ID =
            "00000000-0000-0000-0000-000000000003";

    @TempDir
    Path temporaryDirectory;

    @Mock
    WorkspaceStore store;

    @Mock
    SandboxRuntime runtime;

    @Mock
    WorkspaceTemplateInitializer templates;

    @Mock
    WorkspaceChangeService changes;

    @Mock
    TerminalSessionRegistry terminalSessions;

    private WorkspaceApplicationService service;

    @BeforeEach
    void setUp() {
        WorkspaceProperties properties = properties(temporaryDirectory);
        service = new WorkspaceApplicationService(
                store,
                runtime,
                new WorkspaceDirectoryManager(properties),
                templates,
                changes,
                terminalSessions,
                properties
        );
    }

    @Test
    void initializesTemplateBeforeCreatingWorkspace() {
        when(store.create(
                anyString(),
                anyString(),
                anyString(),
                org.mockito.ArgumentMatchers.any()
        )).thenAnswer(invocation -> workspace(
                invocation.getArgument(0),
                invocation.getArgument(2)
        ));

        Workspace created = service.create(
                "  Demo Workspace  ",
                RuntimeProfile.JAVA_21
        );

        ArgumentCaptor<String> workspaceId = ArgumentCaptor.forClass(
                String.class
        );
        verify(templates).initializeIfEmpty(
                workspaceId.capture(),
                org.mockito.ArgumentMatchers.eq(RuntimeProfile.JAVA_21)
        );
        verify(store).create(
                workspaceId.getValue(),
                OWNER_ID,
                "Demo Workspace",
                RuntimeProfile.JAVA_21
        );
        assertThat(created.id()).isEqualTo(workspaceId.getValue());
    }

    @Test
    void backfillsTemplateWhenReadingExistingWorkspace() {
        Workspace existing = workspace(WORKSPACE_ID, "Existing");
        when(store.find(WORKSPACE_ID, OWNER_ID))
                .thenReturn(Optional.of(existing));
        when(runtime.currentState(null))
                .thenReturn(SandboxRuntime.RuntimeState.ABSENT);

        Workspace result = service.get(WORKSPACE_ID);

        assertThat(result).isEqualTo(existing);
        verify(templates).initializeIfEmpty(
                WORKSPACE_ID,
                RuntimeProfile.JAVA_21
        );
    }

    @Test
    void deleteClosesTerminalAndFileWatcherResources() {
        Instant now = Instant.parse("2026-08-20T00:00:00Z");
        Workspace existing = new Workspace(
                WORKSPACE_ID,
                OWNER_ID,
                "Existing",
                RuntimeProfile.JAVA_21,
                "container-1",
                WorkspaceStatus.STOPPED,
                null,
                now,
                now,
                null
        );
        when(store.find(WORKSPACE_ID, OWNER_ID))
                .thenReturn(Optional.of(existing));
        when(runtime.currentState("container-1"))
                .thenReturn(SandboxRuntime.RuntimeState.STOPPED);

        service.delete(WORKSPACE_ID);

        verify(terminalSessions).closeWorkspace(WORKSPACE_ID);
        verify(changes).closeWorkspace(WORKSPACE_ID);
        verify(runtime).remove("container-1");
        verify(store).delete(WORKSPACE_ID, OWNER_ID);
    }

    private static Workspace workspace(String id, String name) {
        Instant now = Instant.parse("2026-08-20T00:00:00Z");
        return new Workspace(
                id,
                OWNER_ID,
                name,
                RuntimeProfile.JAVA_21,
                null,
                WorkspaceStatus.STOPPED,
                null,
                now,
                now,
                null
        );
    }

    private static WorkspaceProperties properties(Path dataDirectory) {
        return new WorkspaceProperties(
                true,
                OWNER_ID,
                dataDirectory.toString(),
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
