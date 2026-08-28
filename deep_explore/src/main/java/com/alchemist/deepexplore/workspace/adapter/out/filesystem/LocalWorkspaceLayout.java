package com.alchemist.deepexplore.workspace.adapter.out.filesystem;

import com.alchemist.deepexplore.workspace.application.WorkspaceOperationException;
import com.alchemist.deepexplore.workspace.config.WorkspaceProperties;
import com.alchemist.deepexplore.workspace.port.WorkspaceVolumeProvider;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Comparator;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class LocalWorkspaceLayout implements WorkspaceVolumeProvider {

    private final Path workspacesRoot;

    public LocalWorkspaceLayout(WorkspaceProperties properties) {
        this.workspacesRoot = Path.of(properties.dataDirectory())
                .toAbsolutePath()
                .normalize()
                .resolve("workspaces");
        createDirectories(workspacesRoot);
    }

    public void initialize(String workspaceId) {
        Path files = filesDirectory(workspaceId);
        Path maven = mavenCacheDirectory(workspaceId);
        Path npm = npmCacheDirectory(workspaceId);
        Path pnpm = pnpmCacheDirectory(workspaceId);
        createDirectories(files);
        createDirectories(maven);
        createDirectories(npm);
        createDirectories(pnpm);
        makeContainerWritable(files, true);
        makeContainerWritable(maven, true);
        makeContainerWritable(npm, true);
        makeContainerWritable(pnpm, true);
    }

    @Override
    public WorkspaceVolume volume(String workspaceId) {
        initialize(workspaceId);
        return new WorkspaceVolume(
                filesDirectory(workspaceId),
                mavenCacheDirectory(workspaceId),
                npmCacheDirectory(workspaceId),
                pnpmCacheDirectory(workspaceId)
        );
    }

    Path filesDirectory(String workspaceId) {
        return workspaceDirectory(workspaceId).resolve("files");
    }

    private Path mavenCacheDirectory(String workspaceId) {
        return workspaceDirectory(workspaceId)
                .resolve("cache")
                .resolve("m2");
    }

    private Path npmCacheDirectory(String workspaceId) {
        return workspaceDirectory(workspaceId)
                .resolve("cache")
                .resolve("npm");
    }

    private Path pnpmCacheDirectory(String workspaceId) {
        return workspaceDirectory(workspaceId)
                .resolve("cache")
                .resolve("pnpm");
    }

    Path resolveFile(String workspaceId, String relativePath) {
        Path root = filesDirectory(workspaceId).toAbsolutePath().normalize();
        String candidate = relativePath == null ? "" : relativePath.strip();
        if (Path.of(candidate).isAbsolute()) {
            throw invalidPath(relativePath);
        }
        Path resolved = root.resolve(candidate).normalize();
        if (!resolved.startsWith(root)) {
            throw invalidPath(relativePath);
        }
        rejectSymlinkTraversal(root, resolved);
        return resolved;
    }

    String relativePath(String workspaceId, Path path) {
        return filesDirectory(workspaceId)
                .toAbsolutePath()
                .normalize()
                .relativize(path.toAbsolutePath().normalize())
                .toString()
                .replace(path.getFileSystem().getSeparator(), "/");
    }

    void delete(String workspaceId) {
        Path workspace = workspaceDirectory(workspaceId);
        if (!Files.exists(workspace, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var paths = Files.walk(workspace)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException error) {
                    throw new WorkspaceOperationException(
                            "WORKSPACE_DELETE_FAILED",
                            "Unable to delete workspace files",
                            error
                    );
                }
            });
        } catch (IOException error) {
            throw new WorkspaceOperationException(
                    "WORKSPACE_DELETE_FAILED",
                    "Unable to enumerate workspace files",
                    error
            );
        }
    }

    void makeContainerWritable(Path path, boolean directory) {
        try {
            Files.setPosixFilePermissions(
                    path,
                    directory
                            ? Set.of(
                                    PosixFilePermission.OWNER_READ,
                                    PosixFilePermission.OWNER_WRITE,
                                    PosixFilePermission.OWNER_EXECUTE,
                                    PosixFilePermission.GROUP_READ,
                                    PosixFilePermission.GROUP_WRITE,
                                    PosixFilePermission.GROUP_EXECUTE,
                                    PosixFilePermission.OTHERS_READ,
                                    PosixFilePermission.OTHERS_WRITE,
                                    PosixFilePermission.OTHERS_EXECUTE
                            )
                            : Set.of(
                                    PosixFilePermission.OWNER_READ,
                                    PosixFilePermission.OWNER_WRITE,
                                    PosixFilePermission.GROUP_READ,
                                    PosixFilePermission.GROUP_WRITE,
                                    PosixFilePermission.OTHERS_READ,
                                    PosixFilePermission.OTHERS_WRITE
                            )
            );
        } catch (UnsupportedOperationException | IOException ignored) {
            // Docker Desktop handles bind-mount ownership on non-POSIX hosts.
        }
    }

    private Path workspaceDirectory(String workspaceId) {
        if (workspaceId == null
                || !workspaceId.matches("[a-zA-Z0-9-]{1,100}")) {
            throw invalidPath(workspaceId);
        }
        return workspacesRoot.resolve(workspaceId).normalize();
    }

    private static void rejectSymlinkTraversal(Path root, Path resolved) {
        Path current = root;
        for (Path segment : root.relativize(resolved)) {
            current = current.resolve(segment);
            if (Files.isSymbolicLink(current)) {
                throw new WorkspaceOperationException(
                        "INVALID_WORKSPACE_PATH",
                        "Workspace paths must not traverse symbolic links"
                );
            }
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                break;
            }
        }
    }

    private static void createDirectories(Path path) {
        try {
            Files.createDirectories(path);
        } catch (IOException error) {
            throw new WorkspaceOperationException(
                    "WORKSPACE_STORAGE_FAILED",
                    "Unable to initialize workspace storage",
                    error
            );
        }
    }

    private static WorkspaceOperationException invalidPath(String path) {
        return new WorkspaceOperationException(
                "INVALID_WORKSPACE_PATH",
                "Invalid workspace path: " + path
        );
    }
}
