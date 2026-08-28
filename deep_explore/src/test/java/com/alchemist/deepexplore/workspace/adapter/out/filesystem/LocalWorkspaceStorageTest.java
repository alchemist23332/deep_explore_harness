package com.alchemist.deepexplore.workspace.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alchemist.deepexplore.workspace.application.WorkspaceOperationException;
import com.alchemist.deepexplore.workspace.config.WorkspaceProperties;
import com.alchemist.deepexplore.workspace.domain.WorkspaceEntry;
import com.alchemist.deepexplore.workspace.port.WorkspaceStorage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalWorkspaceStorageTest {

    private static final String WORKSPACE_ID =
            "00000000-0000-0000-0000-000000000001";

    @TempDir
    Path temporaryDirectory;

    private LocalWorkspaceLayout directories;
    private LocalWorkspaceStorage files;

    @BeforeEach
    void setUp() {
        WorkspaceProperties properties = properties(temporaryDirectory);
        directories = new LocalWorkspaceLayout(properties);
        directories.initialize(WORKSPACE_ID);
        files = new LocalWorkspaceStorage(directories, properties);
    }

    @Test
    void writesReadsAndListsUtf8Files() {
        var written = files.write(
                WORKSPACE_ID,
                "src/Main.java",
                "class Main {}\n"
        );

        assertThat(files.read(WORKSPACE_ID, "src/Main.java").content())
                .isEqualTo("class Main {}\n");
        assertThat(written.revision()).hasSize(64);
        assertThat(files.list(WORKSPACE_ID, "src"))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.name()).isEqualTo("Main.java");
                    assertThat(entry.path()).isEqualTo("src/Main.java");
                });
    }

    @Test
    void rejectsWriteWhenExpectedRevisionIsStale() {
        var original = files.write(
                WORKSPACE_ID,
                "src/Main.java",
                "first"
        );
        files.write(WORKSPACE_ID, "src/Main.java", "second");

        assertThatThrownBy(() -> files.write(
                WORKSPACE_ID,
                "src/Main.java",
                "stale",
                original.revision()
        ))
                .isInstanceOf(WorkspaceOperationException.class)
                .extracting(error ->
                        ((WorkspaceOperationException) error).code())
                .isEqualTo("WORKSPACE_FILE_CHANGED");
    }

    @Test
    void rejectsPathsOutsideTheWorkspace() {
        assertThatThrownBy(() -> files.read(
                WORKSPACE_ID,
                "../../outside.txt"
        ))
                .isInstanceOf(WorkspaceOperationException.class)
                .hasMessageContaining("Invalid workspace path");
    }

    @Test
    void rejectsSymbolicLinkTraversal() throws Exception {
        Path outside = temporaryDirectory.resolve("outside");
        Files.createDirectories(outside);
        Files.createSymbolicLink(
                directories.filesDirectory(WORKSPACE_ID).resolve("link"),
                outside
        );

        assertThatThrownBy(() -> files.write(
                WORKSPACE_ID,
                "link/escape.txt",
                "no"
        ))
                .isInstanceOf(WorkspaceOperationException.class)
                .hasMessageContaining("symbolic links");
    }

    @Test
    void importsSafeZipAndRejectsZipSlip() throws Exception {
        byte[] safe = zip("src/Main.java", "class Main {}\n");

        WorkspaceStorage.ImportResult result = files.importZip(
                WORKSPACE_ID,
                new ByteArrayInputStream(safe),
                safe.length
        );

        assertThat(result.files()).isEqualTo(1);
        assertThat(files.read(WORKSPACE_ID, "src/Main.java").content())
                .contains("class Main");

        byte[] unsafe = zip("../escape.txt", "no");
        assertThatThrownBy(() -> files.importZip(
                WORKSPACE_ID,
                new ByteArrayInputStream(unsafe),
                unsafe.length
        ))
                .isInstanceOf(WorkspaceOperationException.class)
                .hasMessageContaining("Invalid workspace path");
    }

    @Test
    void rejectsOversizedFiles() {
        assertThatThrownBy(() -> files.write(
                WORKSPACE_ID,
                "large.txt",
                "x".repeat(65)
        ))
                .isInstanceOf(WorkspaceOperationException.class)
                .hasMessageContaining("size limit");
    }

    @Test
    void uploadsFileToPersistentWorkspaceDirectory() {
        byte[] content = "uploaded content\n".getBytes(StandardCharsets.UTF_8);

        WorkspaceStorage.UploadResult result = files.upload(
                WORKSPACE_ID,
                "notes/uploaded.txt",
                new ByteArrayInputStream(content),
                content.length
        );

        assertThat(result.path()).isEqualTo("notes/uploaded.txt");
        assertThat(result.size()).isEqualTo(content.length);
        assertThat(files.read(
                WORKSPACE_ID,
                "notes/uploaded.txt"
        ).content()).isEqualTo("uploaded content\n");
    }

    @Test
    void createsDirectoriesAndFilesAndBuildsNestedTree() {
        files.create(
                WORKSPACE_ID,
                "",
                "src",
                WorkspaceEntry.Type.DIRECTORY
        );
        files.create(
                WORKSPACE_ID,
                "src",
                "Main.java",
                WorkspaceEntry.Type.FILE
        );
        files.create(
                WORKSPACE_ID,
                "",
                "README.md",
                WorkspaceEntry.Type.FILE
        );

        assertThat(files.tree(WORKSPACE_ID))
                .extracting(node -> node.entry().name())
                .containsExactly("src", "README.md");
        assertThat(files.tree(WORKSPACE_ID).getFirst().children())
                .singleElement()
                .satisfies(node -> {
                    assertThat(node.entry().path())
                            .isEqualTo("src/Main.java");
                    assertThat(node.children()).isNull();
                });
    }

    @Test
    void movesFilesAndRejectsConflictsAndSelfNesting() {
        files.create(
                WORKSPACE_ID,
                "",
                "src",
                WorkspaceEntry.Type.DIRECTORY
        );
        files.create(
                WORKSPACE_ID,
                "src",
                "Main.java",
                WorkspaceEntry.Type.FILE
        );
        files.create(
                WORKSPACE_ID,
                "",
                "existing.java",
                WorkspaceEntry.Type.FILE
        );

        WorkspaceEntry moved = files.move(
                WORKSPACE_ID,
                "src/Main.java",
                "src/App.java"
        );

        assertThat(moved.path()).isEqualTo("src/App.java");
        assertThat(files.list(WORKSPACE_ID, "src"))
                .extracting(WorkspaceEntry::name)
                .containsExactly("App.java");
        assertThatThrownBy(() -> files.move(
                WORKSPACE_ID,
                "src/App.java",
                "existing.java"
        ))
                .isInstanceOf(WorkspaceOperationException.class)
                .hasMessageContaining("already exists");
        assertThatThrownBy(() -> files.move(
                WORKSPACE_ID,
                "src",
                "src/nested"
        ))
                .isInstanceOf(WorkspaceOperationException.class)
                .hasMessageContaining("cannot be moved into itself");
    }

    @Test
    void deletesFilesAndRequiresRecursiveDirectoryDeletion() {
        files.create(
                WORKSPACE_ID,
                "",
                "src",
                WorkspaceEntry.Type.DIRECTORY
        );
        files.create(
                WORKSPACE_ID,
                "src",
                "Main.java",
                WorkspaceEntry.Type.FILE
        );

        assertThatThrownBy(() -> files.deleteEntry(
                WORKSPACE_ID,
                "src",
                false
        ))
                .isInstanceOf(WorkspaceOperationException.class)
                .hasMessageContaining("not empty");

        files.deleteEntry(WORKSPACE_ID, "src", true);

        assertThat(files.list(WORKSPACE_ID, "")).isEmpty();
        assertThatThrownBy(() -> files.deleteEntry(WORKSPACE_ID, "", true))
                .isInstanceOf(WorkspaceOperationException.class)
                .hasMessageContaining("root cannot be modified");
    }

    @Test
    void rejectsInvalidEntryNames() {
        assertThatThrownBy(() -> files.create(
                WORKSPACE_ID,
                "",
                "../escape.txt",
                WorkspaceEntry.Type.FILE
        ))
                .isInstanceOf(WorkspaceOperationException.class)
                .hasMessageContaining("Invalid workspace entry name");
    }

    private static WorkspaceProperties properties(Path dataDirectory) {
        return new WorkspaceProperties(
                true,
                "local-user",
                dataDirectory.toString(),
                Duration.ofSeconds(5),
                1024,
                64,
                64,
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

    private static byte[] zip(String path, String content) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream archive = new ZipOutputStream(bytes)) {
            archive.putNextEntry(new ZipEntry(path));
            archive.write(content.getBytes(StandardCharsets.UTF_8));
            archive.closeEntry();
        }
        return bytes.toByteArray();
    }
}
