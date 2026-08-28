package com.alchemist.deepexplore.workspace.application.lifecycle;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceOperationCoordinator {

    private final ConcurrentHashMap<String, ReentrantLock> locks =
            new ConcurrentHashMap<>();

    public <T> T withLock(String workspaceId, Supplier<T> action) {
        ReentrantLock lock = locks.computeIfAbsent(
                workspaceId,
                ignored -> new ReentrantLock()
        );
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    public void forget(String workspaceId) {
        locks.computeIfPresent(workspaceId, (ignored, lock) ->
                lock.isLocked() || lock.hasQueuedThreads() ? lock : null);
    }
}
