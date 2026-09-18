package com.alchemist.deepexplore.coding.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alchemist.deepexplore.coding.application.editing.TextEditOperation;
import com.alchemist.deepexplore.coding.application.editing.TextEditPlanner;
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
import java.util.List;
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
                new TextEditPlanner(),
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
    void editsFileAtomicallyAgainstExpectedRevision() {
        WorkspaceFile existing = new WorkspaceFile(
                "src/App.java",
                "class App {\n    int oldValue = 1;\n}\n",
                38,
                Instant.EPOCH,
                "revision-1"
        );
        WorkspaceFile updated = new WorkspaceFile(
                "src/App.java",
                "class App {\n    int newValue = 2;\n}\n",
                38,
                Instant.EPOCH,
                "revision-2"
        );
        when(storage.read(WORKSPACE_ID, "src/App.java"))
                .thenReturn(existing);
        when(storage.write(
                WORKSPACE_ID,
                "src/App.java",
                updated.content(),
                "revision-1"
        )).thenReturn(updated);

        CodingToolResult result = service.editFile(
                WORKSPACE_ID,
                "src/App.java",
                "revision-1",
                List.of(new TextEditOperation(
                        "int oldValue = 1;",
                        "int newValue = 2;",
                        false
                ))
        );

        assertThat(result.ok()).isTrue();
        assertThat(result.data()).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.MAP
        ).containsEntry("operations", 1)
                .containsEntry("replacements", 1)
                .containsEntry("revision", "revision-2");
        verify(storage).write(
                WORKSPACE_ID,
                "src/App.java",
                updated.content(),
                "revision-1"
        );
    }

    @Test
    void doesNotWriteWhenAnyEditCannotBePlanned() {
        WorkspaceFile existing = new WorkspaceFile(
                "src/App.java",
                "old value",
                9,
                Instant.EPOCH,
                "revision-1"
        );
        when(storage.read(WORKSPACE_ID, "src/App.java"))
                .thenReturn(existing);

        assertThatThrownBy(() -> service.editFile(
                WORKSPACE_ID,
                "src/App.java",
                "revision-1",
                List.of(
                        new TextEditOperation("old", "new", false),
                        new TextEditOperation("missing", "replacement", false)
                )
        ))
                .isInstanceOf(CodingToolException.class)
                .extracting(error -> ((CodingToolException) error).code())
                .isEqualTo("EDIT_TARGET_NOT_FOUND");
        verify(storage, never()).write(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void rejectsEditWhenReadRevisionIsStale() {
        WorkspaceFile existing = new WorkspaceFile(
                "src/App.java",
                "current value",
                13,
                Instant.EPOCH,
                "revision-2"
        );
        when(storage.read(WORKSPACE_ID, "src/App.java"))
                .thenReturn(existing);

        assertThatThrownBy(() -> service.editFile(
                WORKSPACE_ID,
                "src/App.java",
                "revision-1",
                List.of(new TextEditOperation(
                        "current",
                        "updated",
                        false
                ))
        ))
                .isInstanceOf(CodingToolException.class)
                .extracting(error -> ((CodingToolException) error).code())
                .isEqualTo("WORKSPACE_FILE_CHANGED");
        verify(storage, never()).write(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
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
                512,
                12_000,
                32,
                200_000,
                12_000,
                200,
                20_000
        );
    }
}
