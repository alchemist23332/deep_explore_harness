package com.alchemist.deepexplore.workspace.port;

import com.alchemist.deepexplore.workspace.domain.WorkspaceChange;
import reactor.core.publisher.Flux;

public interface WorkspaceChangeSource {

    Flux<WorkspaceChange> changes(String workspaceId);

    void closeWorkspace(String workspaceId);
}
