package com.alchemist.deepexplore.workspace.adapter.out.docker;

import com.alchemist.deepexplore.workspace.application.WorkspaceOperationException;
import com.alchemist.deepexplore.workspace.port.SandboxTerminal;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Component
public class DockerSandboxTerminal implements SandboxTerminal {

    private static final Logger log =
            LoggerFactory.getLogger(DockerSandboxTerminal.class);

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
        } catch (RuntimeException error) {
            log.warn(
                    "Unable to open sandbox terminal for workspace {}",
                    workspaceId,
                    error
            );
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
        private final ByteQueueInputStream stdin = new ByteQueueInputStream();
        private final Sinks.Many<byte[]> output =
                Sinks.many().unicast().onBackpressureBuffer(
                        new ArrayBlockingQueue<>(256)
                );
        private final AtomicBoolean closed = new AtomicBoolean();
        private ResultCallback.Adapter<Frame> callback;

        private DockerTerminalSession(
                String workspaceId,
                String execId
        ) {
            this.workspaceId = workspaceId;
            this.execId = execId;
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
                    log.warn(
                            "Sandbox terminal stream failed (workspace {})",
                            workspaceId,
                            throwable
                    );
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
                stdin.write(data);
            } catch (IOException error) {
                log.warn(
                        "Sandbox terminal stdin write failed (workspace {})",
                        workspaceId,
                        error
                );
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
            stdin.close();
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

    /**
     * Blocking stdin stream without {@link java.io.PipedInputStream}'s
     * thread-affinity checks. Docker-java's hijacking transport reads the
     * exec stdin on one long-lived thread while terminal input is written
     * from a changing pool of scheduler threads; a {@code PipedInputStream}
     * throws "Read/Write end dead" once its last writer thread is retired,
     * which kills otherwise healthy sessions. This queue-based stream has
     * no such coupling.
     */
    private static final class ByteQueueInputStream extends InputStream {

        private final BlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
        private byte[] current;
        private int position;
        private volatile boolean closed;

        void write(byte[] data) throws IOException {
            if (closed) {
                throw new IOException("stream closed");
            }
            if (data.length > 0) {
                queue.add(data);
            }
        }

        @Override
        public int read() throws IOException {
            if (!fill()) {
                return -1;
            }
            return current[position++] & 0xff;
        }

        @Override
        public int read(byte[] buffer, int offset, int length)
                throws IOException {
            if (!fill()) {
                return -1;
            }
            int available = current.length - position;
            int count = Math.min(available, length);
            System.arraycopy(current, position, buffer, offset, count);
            position += count;
            return count;
        }

        private boolean fill() throws IOException {
            while (current == null || position >= current.length) {
                if (closed && queue.isEmpty()) {
                    return false;
                }
                try {
                    current = queue.poll(250, TimeUnit.MILLISECONDS);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted while waiting for "
                            + "terminal input", error);
                }
                if (current != null) {
                    position = 0;
                } else if (closed) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
