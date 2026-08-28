package com.alchemist.deepexplore.coding.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alchemist.deepexplore.coding.config.CodingProperties;
import com.alchemist.deepexplore.workspace.application.command.WorkspaceCommandService;
import com.alchemist.deepexplore.workspace.application.lifecycle.WorkspaceLifecycleService;
import com.alchemist.deepexplore.workspace.application.lifecycle.WorkspaceOperationCoordinator;
import com.alchemist.deepexplore.workspace.application.query.WorkspaceQueryService;
import com.alchemist.deepexplore.workspace.domain.CommandResult;
import com.alchemist.deepexplore.workspace.domain.RuntimeProfile;
import com.alchemist.deepexplore.workspace.domain.Workspace;
import com.alchemist.deepexplore.workspace.domain.WorkspaceFile;
import com.alchemist.deepexplore.workspace.domain.WorkspaceStatus;
import com.alchemist.deepexplore.workspace.port.WorkspaceStorage;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CodingWorkspaceServiceTest {

    private static final String WORKSPACE_ID = "workspace-1";

    private WorkspaceQueryService workspaces;
    private WorkspaceLifecycleService lifecycle;
    private WorkspaceStorage storage;
    private WorkspaceCommandService commands;
    private CodingWorkspaceService service;

    @BeforeEach
    void setUp() {
        workspaces = mock(WorkspaceQueryService.class);
        lifecycle = mock(WorkspaceLifecycleService.class);
        storage = mock(WorkspaceStorage.class);
        commands = mock(WorkspaceCommandService.class);
        when(workspaces.get(WORKSPACE_ID)).thenReturn(workspace(
                WorkspaceStatus.RUNNING
        ));
        service = new CodingWorkspaceService(
                workspaces,
                lifecycle,
                storage,
                commands,
                new WorkspaceOperationCoordinator(),
                properties()
        );
    }

    @Test
    void readsNumberedRangeWithRevision() {
        when(storage.read(WORKSPACE_ID, "src/App.java")).thenReturn(
                new WorkspaceFile(
                        "src/App.java",
                        "one\ntwo\nthree\n",
                        14,
                        Instant.EPOCH,
                        "revision-1"
                )
        );

        CodingToolResult result = service.readFile(
                WORKSPACE_ID,
                "src/App.java",
                2,
                3
        );

        assertThat(result.ok()).isTrue();
        assertThat(result.data()).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.MAP
        ).containsEntry("revision", "revision-1")
                .containsEntry("content", "2: two\n3: three\n");
    }

    @Test
    void requiresRevisionBeforeOverwritingExistingFile() {
        when(storage.read(WORKSPACE_ID, "src/App.java")).thenReturn(
                new WorkspaceFile(
                        "src/App.java",
                        "old",
                        3,
                        Instant.EPOCH,
                        "revision-1"
                )
        );

        assertThatThrownBy(() -> service.writeFile(
                WORKSPACE_ID,
                "src/App.java",
                "new",
                null
        ))
                .isInstanceOf(CodingToolException.class)
                .extracting(error -> ((CodingToolException) error).code())
                .isEqualTo("EXPECTED_REVISION_REQUIRED");
    }

    @Test
    void writesExistingFileWithExpectedRevision() {
        WorkspaceFile existing = new WorkspaceFile(
                "src/App.java",
                "old",
                3,
                Instant.EPOCH,
                "revision-1"
        );
        WorkspaceFile updated = new WorkspaceFile(
                "src/App.java",
                "new",
                3,
                Instant.EPOCH,
                "revision-2"
        );
        when(storage.read(WORKSPACE_ID, "src/App.java"))
                .thenReturn(existing);
        when(storage.write(
                WORKSPACE_ID,
                "src/App.java",
                "new",
                "revision-1"
        )).thenReturn(updated);

        CodingToolResult result = service.writeFile(
                WORKSPACE_ID,
                "src/App.java",
                "new",
                "revision-1"
        );

        assertThat(result.ok()).isTrue();
        verify(storage).write(
                WORKSPACE_ID,
                "src/App.java",
                "new",
                "revision-1"
        );
    }

    @Test
    void reportsNonZeroCommandAsStructuredFailure() {
        when(commands.execute(WORKSPACE_ID, "mvn test", ""))
                .thenReturn(new CommandResult(
                        "mvn test",
                        1,
                        "",
                        "compilation failed",
                        20,
                        false,
                        false
                ));

        CodingToolResult result = service.runCommand(
                WORKSPACE_ID,
                "mvn test",
                ""
        );

        assertThat(result.ok()).isFalse();
        assertThat(result.code()).isEqualTo("COMMAND_FAILED");
        assertThat(result.data()).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.MAP
        ).containsEntry("stderr", "compilation failed");
    }

    @Test
    void rejectsPatchPathsOutsideWorkspace() {
        String patch = """
                --- a/src/App.java
                +++ ../../outside.java
                @@ -1 +1 @@
                -old
                +new
                """;

        assertThatThrownBy(() -> service.applyPatch(WORKSPACE_ID, patch))
                .isInstanceOf(CodingToolException.class)
                .extracting(error -> ((CodingToolException) error).code())
                .isEqualTo("INVALID_PATCH_PATH");
    }

    @Test
    void startsStoppedWorkspaceBeforeCommand() {
        when(workspaces.get(WORKSPACE_ID))
                .thenReturn(workspace(WorkspaceStatus.STOPPED));
        when(commands.execute(WORKSPACE_ID, "pwd", ""))
                .thenReturn(new CommandResult(
                        "pwd",
                        0,
                        "/workspace\n",
                        "",
                        10,
                        false,
                        false
                ));

        CodingToolResult result = service.runCommand(
                WORKSPACE_ID,
                "pwd",
                ""
        );

        assertThat(result.ok()).isTrue();
        verify(lifecycle).start(WORKSPACE_ID);
    }

    private static Workspace workspace(WorkspaceStatus status) {
        return new Workspace(
                WORKSPACE_ID,
                "local-user",
                "Workspace",
                RuntimeProfile.FULLSTACK,
                "container-1",
                status,
                null,
                Instant.EPOCH,
                Instant.EPOCH,
                Instant.EPOCH
        );
    }

    private static CodingProperties properties() {
        return new CodingProperties(
                true,
                20,
                10,
                5,
                12_000,
                12_000,
                200,
                20_000
        );
    }
}
