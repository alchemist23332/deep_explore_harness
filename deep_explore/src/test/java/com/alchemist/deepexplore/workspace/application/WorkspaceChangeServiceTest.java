package com.alchemist.deepexplore.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.alchemist.deepexplore.workspace.domain.WorkspaceChange;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.test.StepVerifier;

class WorkspaceChangeServiceTest {

    private static final String WORKSPACE_ID =
            "00000000-0000-0000-0000-000000000004";

    @TempDir
    Path temporaryDirectory;

    private WorkspaceDirectoryManager directories;
    private WorkspaceChangeService changes;

    @BeforeEach
    void setUp() {
        WorkspaceProperties properties = properties(temporaryDirectory);
        directories = new WorkspaceDirectoryManager(properties);
        directories.initialize(WORKSPACE_ID);
        changes = new WorkspaceChangeService(directories);
    }

    @AfterEach
    void tearDown() {
        changes.close();
    }

    @Test
    void publishesExternalFileChanges() {
        StepVerifier.create(changes.changes(WORKSPACE_ID)
                        .filter(change ->
                                change.path().equals("watched.txt"))
                        .take(1))
                .then(() -> {
                    try {
                        Files.writeString(
                                directories.filesDirectory(WORKSPACE_ID)
                                        .resolve("watched.txt"),
                                "changed"
                        );
                    } catch (java.io.IOException error) {
                        throw new RuntimeException(error);
                    }
                })
                .assertNext(change -> {
                    assertThat(change.kind())
                            .isIn(
                                    WorkspaceChange.Kind.CREATED,
                                    WorkspaceChange.Kind.MODIFIED
                            );
                    assertThat(change.parentPath()).isEmpty();
                })
                .expectComplete()
                .verify(Duration.ofSeconds(3));
    }

    private static WorkspaceProperties properties(Path dataDirectory) {
        return new WorkspaceProperties(
                true,
                "local-user",
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
