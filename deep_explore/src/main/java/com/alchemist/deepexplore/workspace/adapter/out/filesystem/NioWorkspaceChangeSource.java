package com.alchemist.deepexplore.workspace.adapter.out.filesystem;

import com.alchemist.deepexplore.workspace.application.WorkspaceOperationException;
import com.alchemist.deepexplore.workspace.domain.WorkspaceChange;
import com.alchemist.deepexplore.workspace.port.WorkspaceChangeSource;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@Service
public class NioWorkspaceChangeSource implements WorkspaceChangeSource {

    private final LocalWorkspaceLayout directories;
    private final Map<String, WorkspaceWatcher> watchers =
            new ConcurrentHashMap<>();
    private final ExecutorService executor =
            Executors.newVirtualThreadPerTaskExecutor();

    public NioWorkspaceChangeSource(LocalWorkspaceLayout directories) {
        this.directories = directories;
    }

    @Override
    public Flux<WorkspaceChange> changes(String workspaceId) {
        WorkspaceWatcher watcher = watchers.computeIfAbsent(
                workspaceId,
                this::createWatcher
        );
        return watcher.acquire()
                .doFinally(ignored -> release(workspaceId, watcher));
    }

    @Override
    public void closeWorkspace(String workspaceId) {
        WorkspaceWatcher watcher = watchers.remove(workspaceId);
        if (watcher != null) {
            watcher.close();
        }
    }

    private void release(
            String workspaceId,
            WorkspaceWatcher watcher
    ) {
        if (watcher.release() != 0) {
            return;
        }
        if (watchers.remove(workspaceId, watcher)) {
            watcher.close();
        }
    }

    @PreDestroy
    void close() {
        watchers.values().forEach(WorkspaceWatcher::close);
        watchers.clear();
        executor.shutdownNow();
    }

    private WorkspaceWatcher createWatcher(String workspaceId) {
        directories.initialize(workspaceId);
        try {
            WorkspaceWatcher watcher = new WorkspaceWatcher(
                    directories.filesDirectory(workspaceId)
            );
            executor.submit(watcher::run);
            return watcher;
        } catch (IOException error) {
            throw new WorkspaceOperationException(
                    "WORKSPACE_WATCH_FAILED",
                    "Unable to watch workspace files",
                    error
            );
        }
    }

    private final class WorkspaceWatcher {

        private final Path root;
        private final WatchService watchService;
        private final Map<WatchKey, Path> directoriesByKey =
                new ConcurrentHashMap<>();
        private final Sinks.Many<WorkspaceChange> sink =
                Sinks.many().multicast().directBestEffort();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final AtomicInteger subscribers = new AtomicInteger();

        private WorkspaceWatcher(Path root) throws IOException {
            this.root = root.toAbsolutePath().normalize();
            this.watchService = FileSystems.getDefault().newWatchService();
            registerTree(this.root);
        }

        private Flux<WorkspaceChange> acquire() {
            subscribers.incrementAndGet();
            return sink.asFlux();
        }

        private int release() {
            return subscribers.updateAndGet(current ->
                    Math.max(0, current - 1));
        }

        private void run() {
            try {
                while (!closed.get()) {
                    WatchKey key = watchService.take();
                    Path directory = directoriesByKey.get(key);
                    if (directory == null) {
                        key.reset();
                        continue;
                    }
                    for (WatchEvent<?> event : key.pollEvents()) {
                        handle(directory, event);
                    }
                    if (!key.reset()) {
                        directoriesByKey.remove(key);
                    }
                }
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } catch (java.nio.file.ClosedWatchServiceException ignored) {
                // Workspace deletion and application shutdown close the watcher.
            } finally {
                sink.tryEmitComplete();
            }
        }

        private void handle(Path directory, WatchEvent<?> event) {
            if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                emit(WorkspaceChange.Kind.OVERFLOW, "", "");
                return;
            }
            Path child = directory.resolve((Path) event.context())
                    .toAbsolutePath()
                    .normalize();
            if (!child.startsWith(root)) {
                return;
            }
            if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE
                    && Files.isDirectory(child)) {
                try {
                    registerTree(child);
                } catch (IOException ignored) {
                    emit(WorkspaceChange.Kind.OVERFLOW, "", "");
                }
            }
            WorkspaceChange.Kind kind =
                    event.kind() == StandardWatchEventKinds.ENTRY_CREATE
                            ? WorkspaceChange.Kind.CREATED
                            : event.kind()
                                    == StandardWatchEventKinds.ENTRY_DELETE
                                    ? WorkspaceChange.Kind.DELETED
                                    : WorkspaceChange.Kind.MODIFIED;
            String path = relative(child);
            int separator = path.lastIndexOf('/');
            String parentPath = separator < 0
                    ? ""
                    : path.substring(0, separator);
            emit(kind, path, parentPath);
        }

        private void registerTree(Path start) throws IOException {
            try (var paths = Files.walk(start)) {
                paths.filter(Files::isDirectory).forEach(path -> {
                    try {
                        WatchKey key = path.register(
                                watchService,
                                StandardWatchEventKinds.ENTRY_CREATE,
                                StandardWatchEventKinds.ENTRY_MODIFY,
                                StandardWatchEventKinds.ENTRY_DELETE
                        );
                        directoriesByKey.put(key, path);
                    } catch (IOException error) {
                        throw new WatchRegistrationException(error);
                    }
                });
            } catch (WatchRegistrationException error) {
                throw error.cause;
            }
        }

        private String relative(Path path) {
            return root.relativize(path)
                    .toString()
                    .replace(path.getFileSystem().getSeparator(), "/");
        }

        private void emit(
                WorkspaceChange.Kind kind,
                String path,
                String parentPath
        ) {
            sink.tryEmitNext(new WorkspaceChange(
                    kind,
                    path,
                    parentPath,
                    Instant.now()
            ));
        }

        private void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                watchService.close();
            } catch (IOException ignored) {
                // Closing a watcher is best effort during cleanup.
            }
        }
    }

    private static final class WatchRegistrationException
            extends RuntimeException {

        private final IOException cause;

        private WatchRegistrationException(IOException cause) {
            super(cause);
            this.cause = cause;
        }
    }
}
