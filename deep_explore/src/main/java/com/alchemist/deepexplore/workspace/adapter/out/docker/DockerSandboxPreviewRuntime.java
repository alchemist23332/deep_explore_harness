package com.alchemist.deepexplore.workspace.adapter.out.docker;

import com.alchemist.deepexplore.workspace.application.WorkspaceOperationException;
import com.alchemist.deepexplore.workspace.config.PreviewProperties;
import com.alchemist.deepexplore.workspace.domain.CommandResult;
import com.alchemist.deepexplore.workspace.port.SandboxPreviewRuntime;
import com.alchemist.deepexplore.workspace.port.SandboxRuntime;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Ports;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class DockerSandboxPreviewRuntime implements SandboxPreviewRuntime {

    private static final String SESSION = "deep-explore-preview";
    private static final String CONTROL_ROOT = "/tmp/deep-explore-preview";
    private static final String SCRIPT = CONTROL_ROOT + "/run.sh";
    private static final String LOG = CONTROL_ROOT + "/preview.log";
    private static final String EXIT_CODE = CONTROL_ROOT + "/exit-code";

    private final DockerClient docker;
    private final SandboxRuntime runtime;
    private final PreviewProperties properties;

    public DockerSandboxPreviewRuntime(
            DockerClientManager dockerClientManager,
            SandboxRuntime runtime,
            PreviewProperties properties
    ) {
        this.docker = dockerClientManager.client();
        this.runtime = runtime;
        this.properties = properties;
    }

    @Override
    public void start(
            String containerId,
            String command,
            String workingDirectory
    ) {
        if (command == null || command.isBlank()) {
            throw new WorkspaceOperationException(
                    "INVALID_PREVIEW_COMMAND",
                    "Preview command must not be blank"
            );
        }
        String script = """
                #!/usr/bin/env bash
                set +e
                cd -- %s
                %s > %s 2>&1
                status=$?
                printf '%%s' "$status" > %s
                exit "$status"
                """.formatted(
                shellQuote(workingDirectory),
                command,
                shellQuote(LOG),
                shellQuote(EXIT_CODE)
        );
        String encoded = Base64.getEncoder().encodeToString(
                script.getBytes(StandardCharsets.UTF_8)
        );
        String control = "tmux kill-session -t " + SESSION
                + " 2>/dev/null || true; "
                + "mkdir -p " + CONTROL_ROOT + "; "
                + "rm -f " + shellQuote(LOG) + " "
                + shellQuote(EXIT_CODE) + "; "
                + "printf '%s' " + shellQuote(encoded)
                + " | base64 -d > " + shellQuote(SCRIPT) + "; "
                + "chmod 700 " + shellQuote(SCRIPT) + "; "
                + "tmux new-session -d -s " + SESSION + " "
                + shellQuote(SCRIPT);
        requireSuccess(
                runtime.execute(containerId, control, "/workspace"),
                "Unable to start workspace preview"
        );
    }

    @Override
    public ProcessState state(String containerId) {
        if (!containerRunning(containerId)) {
            return ProcessState.STOPPED;
        }
        CommandResult running = runtime.execute(
                containerId,
                "tmux has-session -t " + SESSION + " 2>/dev/null",
                "/workspace"
        );
        if (Integer.valueOf(0).equals(running.exitCode())) {
            return ProcessState.RUNNING;
        }
        CommandResult exitCode = runtime.execute(
                containerId,
                "cat " + shellQuote(EXIT_CODE) + " 2>/dev/null",
                "/workspace"
        );
        if (!Integer.valueOf(0).equals(exitCode.exitCode())) {
            return ProcessState.STOPPED;
        }
        try {
            return Integer.parseInt(exitCode.stdout().strip()) == 0
                    ? ProcessState.SUCCEEDED
                    : ProcessState.FAILED;
        } catch (NumberFormatException ignored) {
            return ProcessState.FAILED;
        }
    }

    @Override
    public String logs(String containerId, int maximumCharacters) {
        if (!containerRunning(containerId)) {
            return "";
        }
        int safeMaximum = Math.max(1, maximumCharacters);
        CommandResult result = runtime.execute(
                containerId,
                "tail -c " + safeMaximum + " "
                        + shellQuote(LOG) + " 2>/dev/null || true",
                "/workspace"
        );
        return result.stdout();
    }

    @Override
    public void stop(String containerId) {
        if (!containerRunning(containerId)) {
            return;
        }
        runtime.execute(
                containerId,
                "tmux kill-session -t " + SESSION
                        + " 2>/dev/null || true",
                "/workspace"
        );
    }

    @Override
    public PreviewAddress address(String containerId) {
        try {
            var inspection = docker.inspectContainerCmd(containerId).exec();
            ExposedPort exposedPort = ExposedPort.tcp(
                    properties.containerPort()
            );
            Ports.Binding[] bindings = inspection.getNetworkSettings()
                    .getPorts()
                    .getBindings()
                    .get(exposedPort);
            if (bindings == null
                    || bindings.length == 0
                    || bindings[0] == null
                    || bindings[0].getHostPortSpec() == null) {
                throw previewUnavailable();
            }
            return new PreviewAddress(
                    properties.host(),
                    Integer.parseInt(bindings[0].getHostPortSpec()),
                    properties.containerPort()
            );
        } catch (NotFoundException error) {
            throw previewUnavailable();
        } catch (NumberFormatException error) {
            throw new WorkspaceOperationException(
                    "PREVIEW_PORT_UNAVAILABLE",
                    "Workspace preview port mapping is invalid",
                    error
            );
        }
    }

    private boolean containerRunning(String containerId) {
        if (containerId == null || containerId.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(
                    docker.inspectContainerCmd(containerId)
                            .exec()
                            .getState()
                            .getRunning()
            );
        } catch (NotFoundException ignored) {
            return false;
        }
    }

    private static void requireSuccess(
            CommandResult result,
            String message
    ) {
        if (result.exitCode() != null && result.exitCode() == 0) {
            return;
        }
        String details = result.stderr().isBlank()
                ? result.stdout()
                : result.stderr();
        throw new WorkspaceOperationException(
                "PREVIEW_START_FAILED",
                message + (details.isBlank() ? "" : ": " + details.strip())
        );
    }

    private static WorkspaceOperationException previewUnavailable() {
        return new WorkspaceOperationException(
                "PREVIEW_PORT_UNAVAILABLE",
                "Restart the workspace to enable its preview port"
        );
    }

    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }
}
