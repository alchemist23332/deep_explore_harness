package com.alchemist.deepexplore.workspace.port;

import com.alchemist.deepexplore.workspace.domain.RuntimeProfile;
import com.alchemist.deepexplore.workspace.domain.Workspace;
import com.alchemist.deepexplore.workspace.domain.WorkspaceStatus;
import java.util.List;
import java.util.Optional;

public interface WorkspaceStore {

    Workspace create(
            String workspaceId,
            String ownerId,
            String name,
            RuntimeProfile runtimeProfile
    );

    Optional<Workspace> find(String workspaceId, String ownerId);

    List<Workspace> list(String ownerId);

    void updateRuntime(
            String workspaceId,
            WorkspaceStatus status,
            String containerId,
            String lastError
    );

    void delete(String workspaceId, String ownerId);
}
