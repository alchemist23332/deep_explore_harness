package com.alchemist.deepexplore.workspace.application.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alchemist.deepexplore.workspace.application.WorkspaceChangeService;
import com.alchemist.deepexplore.workspace.application.WorkspaceTemplateInitializer;
import com.alchemist.deepexplore.workspace.application.query.WorkspaceQueryService;
import com.alchemist.deepexplore.workspace.application.terminal.TerminalSessionRegistry;
import com.alchemist.deepexplore.workspace.config.WorkspaceProperties;
import com.alchemist.deepexplore.workspace.domain.RuntimeProfile;
import com.alchemist.deepexplore.workspace.domain.StarterTemplate;
import com.alchemist.deepexplore.workspace.domain.Workspace;
import com.alchemist.deepexplore.workspace.domain.WorkspaceStatus;
import com.alchemist.deepexplore.workspace.port.SandboxRuntime;
import com.alchemist.deepexplore.workspace.port.SandboxPreviewRuntime;
import com.alchemist.deepexplore.workspace.port.WorkspaceStorage;
import com.alchemist.deepexplore.workspace.port.WorkspaceStore;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkspaceLifecycleServiceTest {

    private static final String OWNER_ID = "local-user";
    private static final String WORKSPACE_ID =
            "00000000-0000-0000-0000-000000000003";

    @Mock
    WorkspaceStore store;

    @Mock
    WorkspaceStorage storage;

    @Mock
    SandboxRuntime runtime;

    @Mock
    SandboxPreviewRuntime previews;

    @Mock
    WorkspaceTemplateInitializer templates;

    @Mock
    WorkspaceChangeService changes;

    @Mock
    TerminalSessionRegistry terminalSessions;

    @Mock
    WorkspaceQueryService queries;

    private WorkspaceLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new WorkspaceLifecycleService(
                store,
                storage,
                runtime,
                previews,
                templates,
                changes,
                terminalSessions,
                queries,
                new WorkspaceOperationCoordinator(),
                properties()
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
                RuntimeProfile.FULLSTACK,
                StarterTemplate.JAVA_MAVEN
        );

        ArgumentCaptor<String> workspaceId = ArgumentCaptor.forClass(
                String.class
        );
        verify(templates).initializeIfEmpty(
                workspaceId.capture(),
                org.mockito.ArgumentMatchers.eq(StarterTemplate.JAVA_MAVEN)
        );
        verify(store).create(
                workspaceId.getValue(),
                OWNER_ID,
                "Demo Workspace",
                RuntimeProfile.FULLSTACK
        );
        assertThat(created.id()).isEqualTo(workspaceId.getValue());
    }

    @Test
    void deleteClosesRuntimeAndPersistentResources() {
        Workspace existing = new Workspace(
                WORKSPACE_ID,
                OWNER_ID,
                "Existing",
                RuntimeProfile.FULLSTACK,
                "container-1",
                WorkspaceStatus.STOPPED,
                null,
                Instant.EPOCH,
                Instant.EPOCH,
                null
        );
        when(queries.get(WORKSPACE_ID)).thenReturn(existing);

        service.delete(WORKSPACE_ID);

        verify(terminalSessions).closeWorkspace(WORKSPACE_ID);
        verify(previews).stop("container-1");
        verify(changes).closeWorkspace(WORKSPACE_ID);
        verify(runtime).remove("container-1");
        verify(storage).deleteWorkspace(WORKSPACE_ID);
        verify(store).delete(WORKSPACE_ID, OWNER_ID);
    }

    private static Workspace workspace(String id, String name) {
        return new Workspace(
                id,
                OWNER_ID,
                name,
                RuntimeProfile.FULLSTACK,
                null,
                WorkspaceStatus.STOPPED,
                null,
                Instant.EPOCH,
                Instant.EPOCH,
                null
        );
    }

    private static WorkspaceProperties properties() {
        return new WorkspaceProperties(
                true,
                OWNER_ID,
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
