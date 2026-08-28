package com.alchemist.deepexplore.workspace.application;

import com.alchemist.deepexplore.workspace.domain.WorkspaceEntry;
import com.alchemist.deepexplore.workspace.domain.WorkspaceFile;
import com.alchemist.deepexplore.workspace.domain.WorkspaceTreeNode;
import com.alchemist.deepexplore.workspace.port.WorkspaceStorage;
import java.io.InputStream;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class WorkspaceFileService {

    private final WorkspaceStorage storage;

    public WorkspaceFileService(WorkspaceStorage storage) {
        this.storage = storage;
    }

    public List<WorkspaceEntry> list(String workspaceId, String path) {
        return storage.list(workspaceId, path);
    }

    public List<WorkspaceTreeNode> tree(String workspaceId) {
        return storage.tree(workspaceId);
    }

    public WorkspaceEntry create(
            String workspaceId,
            String parentPath,
            String name,
            WorkspaceEntry.Type type
    ) {
        return storage.create(workspaceId, parentPath, name, type);
    }

    public WorkspaceEntry move(
            String workspaceId,
            String sourcePath,
            String targetPath
    ) {
        return storage.move(workspaceId, sourcePath, targetPath);
    }

    public void delete(
            String workspaceId,
            String path,
            boolean recursive
    ) {
        storage.deleteEntry(workspaceId, path, recursive);
    }

    public WorkspaceFile read(String workspaceId, String path) {
        return storage.read(workspaceId, path);
    }

    public WorkspaceFile write(
            String workspaceId,
            String path,
            String content
    ) {
        return storage.write(workspaceId, path, content);
    }

    public WorkspaceFile write(
            String workspaceId,
            String path,
            String content,
            String expectedRevision
    ) {
        return storage.write(
                workspaceId,
                path,
                content,
                expectedRevision
        );
    }

    public UploadResult upload(
            String workspaceId,
            String path,
            InputStream content,
            long declaredSize
    ) {
        WorkspaceStorage.UploadResult result = storage.upload(
                workspaceId,
                path,
                content,
                declaredSize
        );
        return new UploadResult(result.path(), result.size());
    }

    public ImportResult importZip(
            String workspaceId,
            InputStream content,
            long declaredSize
    ) {
        WorkspaceStorage.ImportResult result = storage.importZip(
                workspaceId,
                content,
                declaredSize
        );
        return new ImportResult(
                result.files(),
                result.extractedBytes(),
                result.destination()
        );
    }

    public record ImportResult(
            int files,
            long extractedBytes,
            String destination
    ) {
    }

    public record UploadResult(String path, long size) {
    }
}
