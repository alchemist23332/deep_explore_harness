package com.alchemist.deepexplore.workspace.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Comparator;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceDirectoryManager {

    private final Path workspacesRoot;

    public WorkspaceDirectoryManager(WorkspaceProperties properties) {
        this.workspacesRoot = Path.of(properties.dataDirectory())
                .toAbsolutePath()
                .normalize()
                .resolve("workspaces");
        createDirectories(workspacesRoot);
    }

    public void initialize(String workspaceId) {
        Path files = filesDirectory(workspaceId);
        Path maven = mavenCacheDirectory(workspaceId);
        createDirectories(files);
        createDirectories(maven);
        makeContainerWritable(files, true);
        makeContainerWritable(maven, true);
    }

    public Path filesDirectory(String workspaceId) {
        return workspaceDirectory(workspaceId).resolve("files");
    }

    public Path mavenCacheDirectory(String workspaceId) {
        return workspaceDirectory(workspaceId)
                .resolve("cache")
                .resolve("m2");
    }

    public Path resolveFile(String workspaceId, String relativePath) {
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

    public String relativePath(String workspaceId, Path path) {
        return filesDirectory(workspaceId)
                .toAbsolutePath()
                .normalize()
                .relativize(path.toAbsolutePath().normalize())
                .toString()
                .replace(path.getFileSystem().getSeparator(), "/");
    }

    public void delete(String workspaceId) {
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

    public void makeContainerWritable(Path path, boolean directory) {
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
        if (workspaceId == null || !workspaceId.matches("[a-zA-Z0-9-]{1,100}")) {
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
