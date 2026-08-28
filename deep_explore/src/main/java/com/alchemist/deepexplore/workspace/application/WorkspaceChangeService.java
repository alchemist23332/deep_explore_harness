package com.alchemist.deepexplore.workspace.application;

import com.alchemist.deepexplore.workspace.domain.WorkspaceChange;
import com.alchemist.deepexplore.workspace.port.WorkspaceChangeSource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class WorkspaceChangeService {

    private final WorkspaceChangeSource source;

    public WorkspaceChangeService(WorkspaceChangeSource source) {
        this.source = source;
    }

    public Flux<WorkspaceChange> changes(String workspaceId) {
        return source.changes(workspaceId);
    }

    public void closeWorkspace(String workspaceId) {
        source.closeWorkspace(workspaceId);
    }
}
