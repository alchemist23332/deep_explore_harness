package com.alchemist.deepexplore.workspace.port;

import com.alchemist.deepexplore.workspace.domain.CommandResult;
import com.alchemist.deepexplore.workspace.domain.Workspace;

public interface SandboxRuntime {

    Availability availability();

    RuntimeState currentState(String containerId);

    RuntimeInstance start(Workspace workspace);

    void stop(String containerId);

    CommandResult execute(
            String containerId,
            String command,
            String workingDirectory
    );

    void remove(String containerId);

    record Availability(boolean available, String message) {
    }

    record RuntimeInstance(String containerId) {
    }

    enum RuntimeState {
        ABSENT,
        STOPPED,
        RUNNING,
        UNAVAILABLE
    }
}
