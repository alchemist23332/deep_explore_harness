package com.alchemist.deepexplore.workspace.adapter.out.docker;

import com.alchemist.deepexplore.workspace.application.WorkspaceOperationException;
import com.alchemist.deepexplore.workspace.config.PreviewProperties;
import com.alchemist.deepexplore.workspace.config.WorkspaceProperties;
import com.alchemist.deepexplore.workspace.domain.CommandResult;
import com.alchemist.deepexplore.workspace.domain.RuntimeProfile;
import com.alchemist.deepexplore.workspace.domain.Workspace;
import com.alchemist.deepexplore.workspace.port.SandboxRuntime;
import com.alchemist.deepexplore.workspace.port.WorkspaceVolumeProvider;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.Volume;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DockerSandboxRuntime implements SandboxRuntime {

    private static final String RUNTIME_VERSION = "2";
    private static final Logger log =
            LoggerFactory.getLogger(DockerSandboxRuntime.class);

    private final WorkspaceProperties properties;
    private final PreviewProperties previewProperties;
    private final DockerClient docker;
    private final WorkspaceVolumeProvider volumes;

    public DockerSandboxRuntime(
            WorkspaceProperties properties,
            PreviewProperties previewProperties,
            DockerClientManager dockerClientManager,
            WorkspaceVolumeProvider volumes
    ) {
        this.properties = properties;
        this.previewProperties = previewProperties;
        this.docker = dockerClientManager.client();
        this.volumes = volumes;
    }

    @Override
    public Availability availability() {
        if (!properties.enabled()) {
            return new Availability(false, "Docker sandbox is disabled");
        }
        try {
            docker.pingCmd().exec();
            return new Availability(true, "Docker is available");
        } catch (RuntimeException error) {
            log.warn("Docker availability check failed", error);
            return new Availability(
                    false,
                    "Docker is unavailable. Install and start Docker Desktop "
                            + "or Colima."
            );
        }
    }

    @Override
    public RuntimeState currentState(String containerId) {
        if (containerId == null || containerId.isBlank()) {
            return RuntimeState.ABSENT;
        }
        if (!availability().available()) {
            return RuntimeState.UNAVAILABLE;
        }
        try {
            var inspection = docker.inspectContainerCmd(containerId).exec();
            return Boolean.TRUE.equals(inspection.getState().getRunning())
                    ? RuntimeState.RUNNING
                    : RuntimeState.STOPPED;
        } catch (NotFoundException ignored) {
            return RuntimeState.ABSENT;
        } catch (RuntimeException ignored) {
            return RuntimeState.UNAVAILABLE;
        }
    }

    @Override
    public RuntimeInstance start(Workspace workspace) {
        requireAvailable();
        WorkspaceVolumeProvider.WorkspaceVolume volume = volumes.volume(
                workspace.id()
        );
        String containerId = workspace.containerId();
        if (containerId != null && containerExists(containerId)) {
            var inspection = docker.inspectContainerCmd(containerId).exec();
            Map<String, String> labels = inspection.getConfig().getLabels();
            if (labels != null
                    && RUNTIME_VERSION.equals(
                            labels.get("deep-explore.runtime-version")
                    )) {
                if (!Boolean.TRUE.equals(inspection.getState().getRunning())) {
                    docker.startContainerCmd(containerId).exec();
                }
                return new RuntimeInstance(containerId);
            }
            docker.removeContainerCmd(containerId)
                    .withForce(true)
                    .exec();
        }

        ExposedPort previewPort = ExposedPort.tcp(
                previewProperties.containerPort()
        );
        Ports portBindings = new Ports();
        portBindings.bind(
                previewPort,
                Ports.Binding.bindIp(previewProperties.host())
        );
        HostConfig hostConfig = HostConfig.newHostConfig()
                .withBinds(
                        new Bind(
                                volume.filesDirectory().toString(),
                                new Volume("/workspace"),
                                AccessMode.rw
                        ),
                        new Bind(
                                volume.mavenCacheDirectory().toString(),
                                new Volume("/home/agent/.m2"),
                                AccessMode.rw
                        ),
                        new Bind(
                                volume.npmCacheDirectory().toString(),
                                new Volume("/home/agent/.npm"),
                                AccessMode.rw
                        ),
                        new Bind(
                                volume.pnpmCacheDirectory().toString(),
                                new Volume(
                                        "/home/agent/.local/share/pnpm/store"
                                ),
                                AccessMode.rw
                        )
                )
                .withPortBindings(portBindings)
                .withMemory(properties.docker().memoryBytes())
                .withNanoCPUs(properties.docker().nanoCpus())
                .withPidsLimit(properties.docker().pidsLimit())
                .withNetworkMode(properties.docker().networkMode())
                .withCapDrop(Capability.ALL)
                .withSecurityOpts(List.of("no-new-privileges:true"))
                .withAutoRemove(false);

        try {
            var created = docker.createContainerCmd(imageFor(
                            workspace.runtimeProfile()
                    ))
                    .withName("deep-explore-sandbox-" + workspace.id())
                    .withHostConfig(hostConfig)
                    .withExposedPorts(previewPort)
                    .withUser("1000:1000")
                    .withWorkingDir("/workspace")
                    .withLabels(Map.of(
                            "deep-explore.managed", "true",
                            "deep-explore.workspace-id", workspace.id(),
                            "deep-explore.runtime-version", RUNTIME_VERSION
                    ))
                    .withCmd(
                            "/bin/bash",
                            "-lc",
                            "trap : TERM INT; sleep infinity & wait"
                    )
                    .exec();
            docker.startContainerCmd(created.getId()).exec();
            return new RuntimeInstance(created.getId());
        } catch (RuntimeException error) {
            throw dockerError(
                    "Unable to start sandbox. Build the configured runtime "
                            + "image and verify Docker is running.",
                    error
            );
        }
    }

    @Override
    public void stop(String containerId) {
        if (containerId == null || !containerExists(containerId)) {
            return;
        }
        try {
            var state = docker.inspectContainerCmd(containerId)
                    .exec()
                    .getState();
            if (Boolean.TRUE.equals(state.getRunning())) {
                docker.stopContainerCmd(containerId)
                        .withTimeout(10)
                        .exec();
            }
        } catch (RuntimeException error) {
            throw dockerError("Unable to stop sandbox", error);
        }
    }

    @Override
    public CommandResult execute(
            String containerId,
            String command,
            String workingDirectory
    ) {
        requireAvailable();
        if (command == null || command.isBlank()) {
            throw new WorkspaceOperationException(
                    "INVALID_COMMAND",
                    "Command must not be blank"
            );
        }
        ensureRunning(containerId);
        long timeoutSeconds = Math.max(
                1,
                properties.commandTimeout().toSeconds()
        );
        ExecCreateCmdResponse exec = docker.execCreateCmd(containerId)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withWorkingDir(workingDirectory)
                .withCmd(
                        "timeout",
                        "--signal=KILL",
                        timeoutSeconds + "s",
                        "/bin/bash",
                        "-lc",
                        command
                )
                .exec();

        OutputCollector output = new OutputCollector(
                properties.maxCommandOutputBytes()
        );
        Instant startedAt = Instant.now();
        boolean completed;
        try {
            docker.execStartCmd(exec.getId()).exec(output);
            completed = output.awaitCompletion(
                    timeoutSeconds + 5,
                    TimeUnit.SECONDS
            );
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new WorkspaceOperationException(
                    "COMMAND_INTERRUPTED",
                    "Sandbox command was interrupted",
                    error
            );
        } catch (RuntimeException error) {
            throw dockerError("Unable to execute sandbox command", error);
        } finally {
            closeQuietly(output);
        }

        Integer exitCode = null;
        if (completed) {
            Long exitCodeLong = docker.inspectExecCmd(exec.getId())
                    .exec()
                    .getExitCodeLong();
            exitCode = exitCodeLong == null
                    ? null
                    : Math.toIntExact(exitCodeLong);
        }
        long durationMs = Duration.between(
                startedAt,
                Instant.now()
        ).toMillis();
        return new CommandResult(
                command,
                exitCode,
                output.stdout(),
                output.stderr(),
                durationMs,
                !completed || Integer.valueOf(124).equals(exitCode),
                output.truncated()
        );
    }

    @Override
    public void remove(String containerId) {
        if (containerId == null || !containerExists(containerId)) {
            return;
        }
        try {
            docker.removeContainerCmd(containerId)
                    .withForce(true)
                    .exec();
        } catch (RuntimeException error) {
            throw dockerError("Unable to remove sandbox", error);
        }
    }

    private String imageFor(RuntimeProfile profile) {
        return switch (profile) {
            case FULLSTACK -> properties.docker().fullstackImage();
        };
    }

    private boolean containerExists(String containerId) {
        try {
            docker.inspectContainerCmd(containerId).exec();
            return true;
        } catch (NotFoundException ignored) {
            return false;
        }
    }

    private void ensureRunning(String containerId) {
        if (containerId == null || !containerExists(containerId)) {
            throw new WorkspaceOperationException(
                    "SANDBOX_NOT_RUNNING",
                    "Start the workspace sandbox before running commands"
            );
        }
        var state = docker.inspectContainerCmd(containerId)
                .exec()
                .getState();
        if (!Boolean.TRUE.equals(state.getRunning())) {
            throw new WorkspaceOperationException(
                    "SANDBOX_NOT_RUNNING",
                    "Start the workspace sandbox before running commands"
            );
        }
    }

    private void requireAvailable() {
        Availability availability = availability();
        if (!availability.available()) {
            throw new WorkspaceOperationException(
                    "DOCKER_UNAVAILABLE",
                    availability.message()
            );
        }
    }

    private static WorkspaceOperationException dockerError(
            String message,
            RuntimeException error
    ) {
        return new WorkspaceOperationException(
                "SANDBOX_RUNTIME_FAILED",
                message,
                error
        );
    }

    private static void closeQuietly(OutputCollector output) {
        try {
            output.close();
        } catch (IOException ignored) {
            // The collected output is still available.
        }
    }

    private static final class OutputCollector
            extends ResultCallback.Adapter<Frame> {

        private final int limit;
        private final ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        private final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        private int totalBytes;
        private boolean truncated;

        private OutputCollector(int limit) {
            this.limit = limit;
        }

        @Override
        public synchronized void onNext(Frame frame) {
            byte[] payload = frame.getPayload();
            int remaining = limit - totalBytes;
            if (remaining <= 0) {
                truncated = true;
                return;
            }
            int accepted = Math.min(remaining, payload.length);
            ByteArrayOutputStream destination =
                    frame.getStreamType() == StreamType.STDERR
                            ? stderr
                            : stdout;
            destination.write(payload, 0, accepted);
            totalBytes += accepted;
            truncated = truncated || accepted < payload.length;
        }

        private synchronized String stdout() {
            return stdout.toString(StandardCharsets.UTF_8);
        }

        private synchronized String stderr() {
            return stderr.toString(StandardCharsets.UTF_8);
        }

        private synchronized boolean truncated() {
            return truncated;
        }
    }
}
