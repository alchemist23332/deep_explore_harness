package com.alchemist.deepexplore.workspace.port;

import com.alchemist.deepexplore.workspace.domain.WorkspaceEntry;
import com.alchemist.deepexplore.workspace.domain.WorkspaceFile;
import com.alchemist.deepexplore.workspace.domain.WorkspaceTreeNode;
import java.io.InputStream;
import java.util.List;

public interface WorkspaceStorage {

    void initialize(String workspaceId);

    void deleteWorkspace(String workspaceId);

    List<WorkspaceEntry> list(String workspaceId, String path);

    List<WorkspaceTreeNode> tree(String workspaceId);

    WorkspaceEntry create(
            String workspaceId,
            String parentPath,
            String name,
            WorkspaceEntry.Type type
    );

    WorkspaceEntry move(
            String workspaceId,
            String sourcePath,
            String targetPath
    );

    void deleteEntry(String workspaceId, String path, boolean recursive);

    WorkspaceFile read(String workspaceId, String path);

    default WorkspaceFile write(
            String workspaceId,
            String path,
            String content
    ) {
        return write(workspaceId, path, content, null);
    }

    WorkspaceFile write(
            String workspaceId,
            String path,
            String content,
            String expectedRevision
    );

    UploadResult upload(
            String workspaceId,
            String path,
            InputStream content,
            long declaredSize
    );

    ImportResult importZip(
            String workspaceId,
            InputStream content,
            long declaredSize
    );

    String containerWorkingDirectory(
            String workspaceId,
            String relativePath
    );

    record ImportResult(
            int files,
            long extractedBytes,
            String destination
    ) {
    }

    record UploadResult(String path, long size) {
    }
}
