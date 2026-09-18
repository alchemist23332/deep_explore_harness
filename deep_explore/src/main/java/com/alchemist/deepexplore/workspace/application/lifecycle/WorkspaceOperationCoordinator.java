package com.alchemist.deepexplore.workspace.application.lifecycle;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceOperationCoordinator {

    private final ConcurrentHashMap<String, LockState> locks =
            new ConcurrentHashMap<>();

    /**
     * Runs a snapshot-style operation. Multiple readers of the same workspace
     * may proceed together, while a queued or active writer excludes them.
     */
    public <T> T withReadLock(String workspaceId, Supplier<T> action) {
        return withLock(workspaceId, action, LockMode.READ);
    }

    /**
     * Runs an operation that may change workspace files or runtime state.
     */
    public <T> T withWriteLock(String workspaceId, Supplier<T> action) {
        return withLock(workspaceId, action, LockMode.WRITE);
    }

    /**
     * Compatibility alias for callers that have not yet named their intent.
     * The conservative meaning remains exclusive access.
     */
    public <T> T withLock(String workspaceId, Supplier<T> action) {
        return withWriteLock(workspaceId, action);
    }

    private <T> T withLock(
            String workspaceId,
            Supplier<T> action,
            LockMode mode
    ) {
        if (workspaceId == null || workspaceId.isBlank()) {
            throw new IllegalArgumentException("workspaceId must not be blank");
        }
        if (action == null) {
            throw new IllegalArgumentException("action must not be null");
        }

        LockState state = retain(workspaceId);
        Lock lock = mode == LockMode.READ
                ? state.lock.readLock()
                : state.lock.writeLock();
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
            release(workspaceId, state);
        }
    }

    public void forget(String workspaceId) {
        locks.computeIfPresent(workspaceId, (ignored, state) ->
                state.references == 0 ? null : state);
    }

    private LockState retain(String workspaceId) {
        return locks.compute(workspaceId, (ignored, current) -> {
            LockState state = current == null ? new LockState() : current;
            state.references++;
            return state;
        });
    }

    private void release(String workspaceId, LockState expected) {
        locks.compute(workspaceId, (ignored, current) -> {
            if (current != expected) {
                throw new IllegalStateException(
                        "Workspace lock state changed while it was in use"
                );
            }
            current.references--;
            return current.references == 0 ? null : current;
        });
    }

    private enum LockMode {
        READ,
        WRITE
    }

    private static final class LockState {

        private final ReentrantReadWriteLock lock =
                new ReentrantReadWriteLock(true);
        private int references;
    }
}
