package com.alchemist.deepexplore.workspace.port;

public interface SandboxPreviewRuntime {

    void start(
            String containerId,
            String command,
            String workingDirectory
    );

    ProcessState state(String containerId);

    String logs(String containerId, int maximumCharacters);

    void stop(String containerId);

    PreviewAddress address(String containerId);

    enum ProcessState {
        RUNNING,
        SUCCEEDED,
        FAILED,
        STOPPED
    }

    record PreviewAddress(
            String host,
            int hostPort,
            int containerPort
    ) {

        public String url() {
            return "http://" + host + ":" + hostPort;
        }
    }
}
