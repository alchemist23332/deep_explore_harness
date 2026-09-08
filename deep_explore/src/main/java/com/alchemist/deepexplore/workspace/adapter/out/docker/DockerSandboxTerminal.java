package com.alchemist.deepexplore.workspace.adapter.out.docker;

import com.alchemist.deepexplore.workspace.application.WorkspaceOperationException;
import com.alchemist.deepexplore.workspace.port.SandboxTerminal;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class DockerSandboxTerminal implements SandboxTerminal {

    private static final List<String> TERMINAL_ENVIRONMENT = List.of(
            "TERM=xterm-256color",
            "COLORTERM=truecolor",
            "HOME=/home/agent",
            "PROMPT_DIRTRIM=4",
            "PS1=\\[\\e[1;36m\\]\\w\\[\\e[0m\\] \\$ "
    );

    private final DockerClient docker;

    public DockerSandboxTerminal(DockerClientManager dockerClientManager) {
        this.docker = dockerClientManager.client();
    }

    @Override
    public Session open(
            String workspaceId,
            String containerId,
            String workingDirectory,
            int columns,
            int rows
    ) {
        if (containerId == null || containerId.isBlank()) {
            throw new WorkspaceOperationException(
                    "SANDBOX_NOT_RUNNING",
                    "Start the workspace sandbox before opening a terminal"
            );
        }
        try {
            String execId = docker.execCreateCmd(containerId)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withTty(true)
                    .withWorkingDir(workingDirectory)
                    .withEnv(TERMINAL_ENVIRONMENT)
                    .withCmd(
                            "/bin/bash",
                            "--noprofile",
                            "--norc",
                            "-i"
                    )
                    .exec()
                    .getId();
            DockerTerminalSession session = new DockerTerminalSession(
                    workspaceId,
                    execId
            );
            session.start(columns, rows);
            return session;
        } catch (IOException | RuntimeException error) {
            throw new WorkspaceOperationException(
                    "TERMINAL_OPEN_FAILED",
                    "Unable to open sandbox terminal",
                    error
            );
        }
    }

    private final class DockerTerminalSession implements Session {

        private final String id = UUID.randomUUID().toString();
        private final String workspaceId;
        private final String execId;
        private final PipedInputStream stdin;
        private final PipedOutputStream input;
        private final Sinks.Many<byte[]> output =
                Sinks.many().unicast().onBackpressureBuffer(
                        new ArrayBlockingQueue<>(256)
                );
        private final AtomicBoolean closed = new AtomicBoolean();
        private ResultCallback.Adapter<Frame> callback;

        private DockerTerminalSession(
                String workspaceId,
                String execId
        ) throws IOException {
            this.workspaceId = workspaceId;
            this.execId = execId;
            this.stdin = new PipedInputStream(64 * 1024);
            this.input = new PipedOutputStream(stdin);
        }

        private void start(int columns, int rows) {
            callback = new ResultCallback.Adapter<>() {
                @Override
                public void onNext(Frame frame) {
                    byte[] payload = frame.getPayload();
                    Sinks.EmitResult result = output.tryEmitNext(Arrays.copyOf(
                            payload,
                            payload.length
                    ));
                    if (result == Sinks.EmitResult.FAIL_OVERFLOW) {
                        DockerTerminalSession.this.close();
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    if (!closed.get()) {
                        output.tryEmitError(throwable);
                    }
                }

                @Override
                public void onComplete() {
                    output.tryEmitComplete();
                }
            };
            docker.execStartCmd(execId)
                    .withTty(true)
                    .withStdIn(stdin)
                    .exec(callback);
            resize(columns, rows);
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Flux<byte[]> output() {
            return output.asFlux();
        }

        @Override
        public synchronized void input(byte[] data) {
            if (closed.get()) {
                return;
            }
            try {
                input.write(data);
                input.flush();
            } catch (IOException error) {
                close();
            }
        }

        @Override
        public void resize(int columns, int rows) {
            if (closed.get()) {
                return;
            }
            int safeColumns = Math.max(20, Math.min(columns, 500));
            int safeRows = Math.max(5, Math.min(rows, 200));
            try {
                docker.resizeExecCmd(execId)
                        .withSize(safeRows, safeColumns)
                        .exec();
            } catch (RuntimeException ignored) {
                // The first resize may race with exec startup.
            }
        }

        @Override
        public synchronized void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                input.close();
            } catch (IOException ignored) {
                // Continue closing the remaining terminal resources.
            }
            try {
                stdin.close();
            } catch (IOException ignored) {
                // Continue closing the remaining terminal resources.
            }
            if (callback != null) {
                try {
                    callback.close();
                } catch (IOException ignored) {
                    // The Docker stream may already be closed.
                }
            }
            output.tryEmitComplete();
        }

        @Override
        public String toString() {
            return "DockerTerminalSession[%s:%s]".formatted(
                    workspaceId,
                    id
            );
        }
    }
}
