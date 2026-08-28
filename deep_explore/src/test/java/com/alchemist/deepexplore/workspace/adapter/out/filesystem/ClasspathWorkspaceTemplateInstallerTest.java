package com.alchemist.deepexplore.workspace.adapter.out.filesystem;

import static org.assertj.core.api.Assertions.assertThat;

import com.alchemist.deepexplore.workspace.config.WorkspaceProperties;
import com.alchemist.deepexplore.workspace.domain.StarterTemplate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;

class ClasspathWorkspaceTemplateInstallerTest {

    private static final String WORKSPACE_ID =
            "00000000-0000-0000-0000-000000000002";

    @TempDir
    Path temporaryDirectory;

    private LocalWorkspaceLayout directories;
    private ClasspathWorkspaceTemplateInstaller templates;

    @BeforeEach
    void setUp() {
        WorkspaceProperties properties = properties(temporaryDirectory);
        directories = new LocalWorkspaceLayout(properties);
        templates = new ClasspathWorkspaceTemplateInstaller(
                directories,
                new DefaultResourceLoader()
        );
    }

    @Test
    void initializesJava21MavenProject() throws Exception {
        templates.initializeIfEmpty(
                WORKSPACE_ID,
                StarterTemplate.JAVA_MAVEN
        );

        Path root = directories.filesDirectory(WORKSPACE_ID);
        assertThat(root.resolve("README.md")).hasContent(
                "# Java 21 Workspace\n\n"
                        + "This workspace contains a minimal Maven project.\n\n"
                        + "## Commands\n\n"
                        + "```bash\n"
                        + "mvn test\n"
                        + "mvn package\n"
                        + "java -cp target/classes com.example.App\n"
                        + "```\n\n"
                        + "Workspace files persist when the sandbox is stopped "
                        + "or recreated.\n"
        );
        assertThat(root.resolve("pom.xml"))
                .content()
                .contains("<maven.compiler.release>21")
                .contains("maven-compiler-plugin")
                .contains("<version>3.13.0</version>")
                .contains("junit-jupiter");
        assertThat(root.resolve(
                "src/main/java/com/example/App.java"
        )).exists();
        assertThat(root.resolve(
                "src/test/java/com/example/AppTest.java"
        )).exists();
    }

    @Test
    void initializesWebTypeScriptProject() {
        templates.initializeIfEmpty(
                WORKSPACE_ID,
                StarterTemplate.WEB_TYPESCRIPT
        );

        Path root = directories.filesDirectory(WORKSPACE_ID);
        assertThat(root.resolve("package.json"))
                .content()
                .contains("\"vite\"")
                .contains("\"typescript\"");
        assertThat(root.resolve("src/main.ts")).exists();
        assertThat(root.resolve("index.html")).exists();
    }

    @Test
    void initializesEmptyProjectWithoutLanguageBuildFiles() {
        templates.initializeIfEmpty(
                WORKSPACE_ID,
                StarterTemplate.EMPTY
        );

        Path root = directories.filesDirectory(WORKSPACE_ID);
        assertThat(root.resolve("README.md")).exists();
        assertThat(root.resolve("pom.xml")).doesNotExist();
        assertThat(root.resolve("package.json")).doesNotExist();
    }

    @Test
    void preservesFilesAfterProjectWasInitialized() throws Exception {
        templates.initializeIfEmpty(
                WORKSPACE_ID,
                StarterTemplate.JAVA_MAVEN
        );
        Path readme = directories.filesDirectory(WORKSPACE_ID)
                .resolve("README.md");
        Files.writeString(readme, "custom content");

        templates.initializeIfEmpty(
                WORKSPACE_ID,
                StarterTemplate.JAVA_MAVEN
        );

        assertThat(readme).hasContent("custom content");
    }

    @Test
    void doesNotInjectTemplateIntoNonEmptyWorkspace() throws Exception {
        directories.initialize(WORKSPACE_ID);
        Path root = directories.filesDirectory(WORKSPACE_ID);
        Files.writeString(root.resolve("existing.txt"), "keep");

        templates.initializeIfEmpty(
                WORKSPACE_ID,
                StarterTemplate.JAVA_MAVEN
        );

        assertThat(root.resolve("existing.txt")).hasContent("keep");
        assertThat(root.resolve("pom.xml")).doesNotExist();
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
