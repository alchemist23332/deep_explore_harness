package com.alchemist.deepexplore.workspace.adapter.out.filesystem;

import com.alchemist.deepexplore.workspace.application.WorkspaceOperationException;
import com.alchemist.deepexplore.workspace.config.WorkspaceProperties;
import com.alchemist.deepexplore.workspace.domain.WorkspaceEntry;
import com.alchemist.deepexplore.workspace.domain.WorkspaceFile;
import com.alchemist.deepexplore.workspace.domain.WorkspaceTreeNode;
import com.alchemist.deepexplore.workspace.port.WorkspaceStorage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
final class LocalWorkspaceStorage implements WorkspaceStorage {

    private static final Logger log =
            LoggerFactory.getLogger(LocalWorkspaceStorage.class);

    private static final int MAX_TREE_DEPTH = 64;
    private static final int MAX_TREE_ENTRIES = 20_000;

    private final LocalWorkspaceLayout directories;
    private final WorkspaceProperties properties;

    LocalWorkspaceStorage(
            LocalWorkspaceLayout directories,
            WorkspaceProperties properties
    ) {
        this.directories = directories;
        this.properties = properties;
    }

    @Override
    public void initialize(String workspaceId) {
        directories.initialize(workspaceId);
    }

    @Override
    public void deleteWorkspace(String workspaceId) {
        directories.delete(workspaceId);
    }

    @Override
    public List<WorkspaceEntry> list(String workspaceId, String path) {
        Path directory = directories.resolveFile(workspaceId, path);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new WorkspaceOperationException(
                    "WORKSPACE_DIRECTORY_NOT_FOUND",
                    "Workspace directory does not exist"
            );
        }
        try (var entries = Files.list(directory)) {
            return entries
                    .filter(entry -> !Files.isSymbolicLink(entry))
                    .map(entry -> tryToEntry(workspaceId, entry))
                    .filter(Objects::nonNull)
                    .sorted(Comparator
                            .<WorkspaceEntry>comparingInt(entry ->
                                    entry.type()
                                            == WorkspaceEntry.Type.DIRECTORY
                                            ? 0
                                            : 1)
                            .thenComparing(
                                    WorkspaceEntry::name,
                                    String.CASE_INSENSITIVE_ORDER
                            ))
                    .toList();
        } catch (IOException error) {
            throw fileError("Unable to list workspace files", error);
        }
    }

    public List<WorkspaceTreeNode> tree(String workspaceId) {
        Path root = directories.filesDirectory(workspaceId);
        var counter = new java.util.concurrent.atomic.AtomicInteger();
        return treeChildren(workspaceId, root, 0, counter);
    }

    public WorkspaceEntry create(
            String workspaceId,
            String parentPath,
            String name,
            WorkspaceEntry.Type type
    ) {
        validateEntryName(name);
        Path parent = directories.resolveFile(workspaceId, parentPath);
        if (!Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw new WorkspaceOperationException(
                    "WORKSPACE_DIRECTORY_NOT_FOUND",
                    "Parent workspace directory does not exist"
            );
        }
        Path target = directories.resolveFile(
                workspaceId,
                joinPath(parentPath, name)
        );
        try {
            if (type == WorkspaceEntry.Type.DIRECTORY) {
                Files.createDirectory(target);
                directories.makeContainerWritable(target, true);
            } else {
                Files.createFile(target);
                directories.makeContainerWritable(target, false);
            }
            return toEntry(workspaceId, target);
        } catch (FileAlreadyExistsException error) {
            throw conflict("Workspace entry already exists", error);
        } catch (IOException error) {
            throw fileError("Unable to create workspace entry", error);
        }
    }

    public WorkspaceEntry move(
            String workspaceId,
            String sourcePath,
            String targetPath
    ) {
        Path source = requireMutableEntry(workspaceId, sourcePath);
        Path target = requireNonRootPath(workspaceId, targetPath);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw conflict("Target workspace entry already exists", null);
        }
        Path targetParent = target.getParent();
        if (!Files.isDirectory(targetParent, LinkOption.NOFOLLOW_LINKS)) {
            throw new WorkspaceOperationException(
                    "WORKSPACE_DIRECTORY_NOT_FOUND",
                    "Target workspace directory does not exist"
            );
        }
        if (Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)
                && target.startsWith(source)) {
            throw new WorkspaceOperationException(
                    "INVALID_WORKSPACE_MOVE",
                    "A directory cannot be moved into itself"
            );
        }
        try {
            moveWithoutReplace(source, target);
            return toEntry(workspaceId, target);
        } catch (FileAlreadyExistsException error) {
            throw conflict("Target workspace entry already exists", error);
        } catch (IOException error) {
            throw fileError("Unable to move workspace entry", error);
        }
    }

    @Override
    public void deleteEntry(
            String workspaceId,
            String path,
            boolean recursive
    ) {
        Path target = requireMutableEntry(workspaceId, path);
        try {
            if (recursive && Files.isDirectory(
                    target,
                    LinkOption.NOFOLLOW_LINKS
            )) {
                try (var entries = Files.walk(target)) {
                    entries.sorted(Comparator.reverseOrder()).forEach(entry -> {
                        try {
                            Files.delete(entry);
                        } catch (IOException error) {
                            throw fileError(
                                    "Unable to delete workspace entry",
                                    error
                            );
                        }
                    });
                }
                return;
            }
            Files.delete(target);
        } catch (DirectoryNotEmptyException error) {
            throw new WorkspaceOperationException(
                    "WORKSPACE_DIRECTORY_NOT_EMPTY",
                    "Workspace directory is not empty"
            );
        } catch (IOException error) {
            throw fileError("Unable to delete workspace entry", error);
        }
    }

    @Override
    public WorkspaceFile read(String workspaceId, String path) {
        Path file = directories.resolveFile(workspaceId, path);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new WorkspaceOperationException(
                    "WORKSPACE_FILE_NOT_FOUND",
                    "Workspace file does not exist"
            );
        }
        try {
            long size = Files.size(file);
            if (size > properties.maxPreviewBytes()) {
                throw new WorkspaceOperationException(
                        "WORKSPACE_FILE_TOO_LARGE",
                        "File is too large to preview"
                );
            }
            byte[] bytes = Files.readAllBytes(file);
            return new WorkspaceFile(
                    directories.relativePath(workspaceId, file),
                    decodeText(bytes),
                    size,
                    Files.getLastModifiedTime(file).toInstant(),
                    revision(bytes)
            );
        } catch (IOException error) {
            throw fileError("Unable to read workspace file", error);
        }
    }

    @Override
    public WorkspaceFile write(
            String workspaceId,
            String path,
            String content,
            String expectedRevision
    ) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > properties.maxFileBytes()) {
            throw new WorkspaceOperationException(
                    "WORKSPACE_FILE_TOO_LARGE",
                    "File exceeds the workspace file size limit"
            );
        }
        Path file = directories.resolveFile(workspaceId, path);
        verifyRevision(file, expectedRevision);
        writeAtomically(file, bytes);
        directories.makeContainerWritable(file, false);
        return read(workspaceId, path);
    }

    public UploadResult upload(
            String workspaceId,
            String path,
            InputStream content,
            long declaredSize
    ) {
        if (declaredSize > properties.maxFileBytes()) {
            throw new WorkspaceOperationException(
                    "WORKSPACE_FILE_TOO_LARGE",
                    "File exceeds the workspace file size limit"
            );
        }
        Path file = directories.resolveFile(workspaceId, path);
        try {
            Files.createDirectories(file.getParent());
            Path temporary = Files.createTempFile(
                    file.getParent(),
                    ".upload-",
                    ".tmp"
            );
            try {
                copyLimited(content, temporary, properties.maxFileBytes());
                moveReplacing(temporary, file);
                directories.makeContainerWritable(file, false);
            } finally {
                Files.deleteIfExists(temporary);
            }
            return new UploadResult(
                    directories.relativePath(workspaceId, file),
                    Files.size(file)
            );
        } catch (IOException error) {
            throw fileError("Unable to upload workspace file", error);
        }
    }

    public ImportResult importZip(
            String workspaceId,
            InputStream content,
            long declaredSize
    ) {
        if (declaredSize > properties.maxZipBytes()) {
            throw new WorkspaceOperationException(
                    "WORKSPACE_ARCHIVE_TOO_LARGE",
                    "ZIP archive exceeds the upload size limit"
            );
        }
        Path root = directories.filesDirectory(workspaceId);
        int files = 0;
        long extractedBytes = 0;
        try (ZipArchiveInputStream archive = new ZipArchiveInputStream(
                content,
                StandardCharsets.UTF_8.name(),
                true,
                true
        )) {
            ZipArchiveEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = archive.getNextEntry()) != null) {
                validateArchiveEntry(entry);
                Path target = directories.resolveFile(
                        workspaceId,
                        entry.getName()
                );
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    directories.makeContainerWritable(target, true);
                    continue;
                }
                files++;
                if (files > properties.maxExtractedFiles()) {
                    throw new WorkspaceOperationException(
                            "WORKSPACE_ARCHIVE_TOO_MANY_FILES",
                            "ZIP archive contains too many files"
                    );
                }
                Files.createDirectories(target.getParent());
                directories.makeContainerWritable(target.getParent(), true);
                try (var output = Files.newOutputStream(target)) {
                    int read;
                    while ((read = archive.read(buffer)) >= 0) {
                        if (read == 0) {
                            continue;
                        }
                        extractedBytes += read;
                        if (extractedBytes > properties.maxExtractedBytes()) {
                            throw new WorkspaceOperationException(
                                    "WORKSPACE_ARCHIVE_TOO_LARGE",
                                    "ZIP archive exceeds the extracted size limit"
                            );
                        }
                        output.write(buffer, 0, read);
                    }
                }
                directories.makeContainerWritable(target, false);
            }
            return new ImportResult(files, extractedBytes, root.toString());
        } catch (IOException error) {
            throw fileError("Unable to import ZIP archive", error);
        }
    }

    @Override
    public String containerWorkingDirectory(
            String workspaceId,
            String relativePath
    ) {
        Path directory = directories.resolveFile(workspaceId, relativePath);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new WorkspaceOperationException(
                    "WORKSPACE_DIRECTORY_NOT_FOUND",
                    "Command working directory does not exist"
            );
        }
        String relative = directories.relativePath(workspaceId, directory);
        return relative.isBlank() ? "/workspace" : "/workspace/" + relative;
    }

    private WorkspaceEntry toEntry(String workspaceId, Path entry) {
        try {
            boolean directory = Files.isDirectory(
                    entry,
                    LinkOption.NOFOLLOW_LINKS
            );
            return new WorkspaceEntry(
                    entry.getFileName().toString(),
                    directories.relativePath(workspaceId, entry),
                    directory
                            ? WorkspaceEntry.Type.DIRECTORY
                            : WorkspaceEntry.Type.FILE,
                    directory ? 0 : Files.size(entry),
                    Files.getLastModifiedTime(
                            entry,
                            LinkOption.NOFOLLOW_LINKS
                    ).toInstant()
            );
        } catch (IOException error) {
            throw fileError("Unable to inspect workspace file", error);
        }
    }

    /**
     * Entries created inside the sandbox container (for example the
     * {@code node_modules/.bin} symlinks npm installs) can surface on a
     * Windows host as reparse points that are neither reported as symbolic
     * links by {@link Files#isSymbolicLink} nor stat-able by the NIO API.
     * They are skipped instead of failing the entire listing or tree.
     */
    private WorkspaceEntry tryToEntry(String workspaceId, Path entry) {
        try {
            return toEntry(workspaceId, entry);
        } catch (WorkspaceOperationException error) {
            log.debug(
                    "Skipping unstattable workspace entry {}",
                    entry,
                    error
            );
            return null;
        }
    }

    private List<WorkspaceTreeNode> treeChildren(
            String workspaceId,
            Path directory,
            int depth,
            java.util.concurrent.atomic.AtomicInteger counter
    ) {
        if (depth > MAX_TREE_DEPTH) {
            throw new WorkspaceOperationException(
                    "WORKSPACE_TREE_TOO_DEEP",
                    "Workspace tree exceeds the maximum depth"
            );
        }
        try (var entries = Files.list(directory)) {
            return entries
                    .filter(entry -> !Files.isSymbolicLink(entry))
                    .map(entry -> {
                        if (counter.incrementAndGet() > MAX_TREE_ENTRIES) {
                            throw new WorkspaceOperationException(
                                    "WORKSPACE_TREE_TOO_LARGE",
                                    "Workspace tree contains too many entries"
                            );
                        }
                        WorkspaceEntry value = tryToEntry(workspaceId, entry);
                        if (value == null) {
                            return null;
                        }
                        List<WorkspaceTreeNode> children =
                                value.type() == WorkspaceEntry.Type.DIRECTORY
                                        ? treeChildren(
                                                workspaceId,
                                                entry,
                                                depth + 1,
                                                counter
                                        )
                                        : null;
                        return new WorkspaceTreeNode(value, children);
                    })
                    .filter(Objects::nonNull)
                    .sorted(Comparator
                            .comparingInt((WorkspaceTreeNode node) ->
                                    node.entry().type()
                                            == WorkspaceEntry.Type.DIRECTORY
                                            ? 0
                                            : 1)
                            .thenComparing(
                                    node -> node.entry().name(),
                                    String.CASE_INSENSITIVE_ORDER
                            ))
                    .toList();
        } catch (IOException error) {
            throw fileError("Unable to build workspace tree", error);
        }
    }

    private Path requireMutableEntry(String workspaceId, String path) {
        Path target = requireNonRootPath(workspaceId, path);
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new WorkspaceOperationException(
                    "WORKSPACE_ENTRY_NOT_FOUND",
                    "Workspace entry does not exist"
            );
        }
        return target;
    }

    private Path requireNonRootPath(String workspaceId, String path) {
        if (path == null || path.isBlank()) {
            throw new WorkspaceOperationException(
                    "INVALID_WORKSPACE_PATH",
                    "The workspace root cannot be modified"
            );
        }
        return directories.resolveFile(workspaceId, path);
    }

    private static void validateEntryName(String name) {
        if (name == null
                || name.isBlank()
                || name.equals(".")
                || name.equals("..")
                || name.indexOf('/') >= 0
                || name.indexOf('\\') >= 0
                || name.indexOf('\0') >= 0) {
            throw new WorkspaceOperationException(
                    "INVALID_WORKSPACE_NAME",
                    "Invalid workspace entry name"
            );
        }
    }

    private static String joinPath(String parentPath, String name) {
        return parentPath == null || parentPath.isBlank()
                ? name
                : parentPath.strip() + "/" + name;
    }

    private static String decodeText(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException error) {
            throw new WorkspaceOperationException(
                    "WORKSPACE_FILE_NOT_TEXT",
                    "Only UTF-8 text files can be previewed"
            );
        }
    }

    private static void verifyRevision(Path file, String expectedRevision) {
        if (expectedRevision == null || expectedRevision.isBlank()) {
            return;
        }
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new WorkspaceOperationException(
                        "WORKSPACE_FILE_CHANGED",
                        "Workspace file no longer matches the version that was read"
                );
            }
            String actualRevision = revision(Files.readAllBytes(file));
            if (!MessageDigest.isEqual(
                    actualRevision.getBytes(StandardCharsets.US_ASCII),
                    expectedRevision.getBytes(StandardCharsets.US_ASCII)
            )) {
                throw new WorkspaceOperationException(
                        "WORKSPACE_FILE_CHANGED",
                        "Workspace file changed after it was read"
                );
            }
        } catch (IOException error) {
            throw fileError("Unable to verify workspace file revision", error);
        }
    }

    private static String revision(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)
            );
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static void validateArchiveEntry(ZipArchiveEntry entry) {
        if (entry.isUnixSymlink()) {
            throw new WorkspaceOperationException(
                    "WORKSPACE_ARCHIVE_UNSAFE",
                    "ZIP archive must not contain symbolic links"
            );
        }
        String name = entry.getName();
        if (name == null || name.isBlank() || name.indexOf('\0') >= 0) {
            throw new WorkspaceOperationException(
                    "WORKSPACE_ARCHIVE_UNSAFE",
                    "ZIP archive contains an invalid path"
            );
        }
    }

    private static void writeAtomically(Path file, byte[] bytes) {
        try {
            Files.createDirectories(file.getParent());
            Path temporary = Files.createTempFile(
                    file.getParent(),
                    ".write-",
                    ".tmp"
            );
            try {
                Files.write(temporary, bytes);
                moveReplacing(temporary, file);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException error) {
            throw fileError("Unable to write workspace file", error);
        }
    }

    private static void copyLimited(
            InputStream input,
            Path destination,
            long limit
    ) throws IOException {
        try (var output = Files.newOutputStream(destination)) {
            byte[] buffer = new byte[8192];
            long total = 0;
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                total += read;
                if (total > limit) {
                    throw new WorkspaceOperationException(
                            "WORKSPACE_FILE_TOO_LARGE",
                            "File exceeds the workspace file size limit"
                    );
                }
                output.write(buffer, 0, read);
            }
        }
    }

    private static void moveReplacing(Path source, Path destination)
            throws IOException {
        try {
            Files.move(
                    source,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(
                    source,
                    destination,
                    StandardCopyOption.REPLACE_EXISTING
            );
        }
    }

    private static void moveWithoutReplace(Path source, Path destination)
            throws IOException {
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(source, destination);
        }
    }

    private static WorkspaceOperationException conflict(
            String message,
            Exception error
    ) {
        return new WorkspaceOperationException(
                "WORKSPACE_ENTRY_CONFLICT",
                message,
                error
        );
    }

    private static WorkspaceOperationException fileError(
            String message,
            Exception error
    ) {
        if (error instanceof WorkspaceOperationException workspaceError) {
            return workspaceError;
        }
        return new WorkspaceOperationException(
                "WORKSPACE_FILE_OPERATION_FAILED",
                message,
                error
        );
    }

}
