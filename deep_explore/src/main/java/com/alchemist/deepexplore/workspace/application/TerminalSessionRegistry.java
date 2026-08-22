package com.alchemist.deepexplore.workspace.application;

import com.alchemist.deepexplore.workspace.port.SandboxTerminal;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class TerminalSessionRegistry {

    private static final int MAX_SESSIONS_PER_WORKSPACE = 3;

    private final SandboxTerminal terminal;
    private final Map<String, ManagedSession> sessions =
            new ConcurrentHashMap<>();
    private final Map<String, Set<String>> sessionsByWorkspace =
            new ConcurrentHashMap<>();

    public TerminalSessionRegistry(SandboxTerminal terminal) {
        this.terminal = terminal;
    }

    public SandboxTerminal.Session open(
            String workspaceId,
            String containerId,
            int columns,
            int rows
    ) {
        Set<String> workspaceSessions = sessionsByWorkspace.computeIfAbsent(
                workspaceId,
                ignored -> ConcurrentHashMap.newKeySet()
        );
        synchronized (workspaceSessions) {
            if (workspaceSessions.size() >= MAX_SESSIONS_PER_WORKSPACE) {
                throw new WorkspaceOperationException(
                        "TERMINAL_SESSION_LIMIT",
                        "Workspace terminal session limit reached"
                );
            }
            SandboxTerminal.Session delegate = terminal.open(
                    workspaceId,
                    containerId,
                    "/workspace",
                    columns,
                    rows
            );
            ManagedSession managed = new ManagedSession(
                    workspaceId,
                    delegate
            );
            sessions.put(managed.id(), managed);
            workspaceSessions.add(managed.id());
            return managed;
        }
    }

    public void closeWorkspace(String workspaceId) {
        Set<String> workspaceSessions = sessionsByWorkspace.remove(workspaceId);
        if (workspaceSessions == null) {
            return;
        }
        workspaceSessions.forEach(sessionId -> {
            ManagedSession session = sessions.remove(sessionId);
            if (session != null) {
                session.closeDelegate();
            }
        });
    }

    @PreDestroy
    void close() {
        sessions.values().forEach(ManagedSession::closeDelegate);
        sessions.clear();
        sessionsByWorkspace.clear();
    }

    private void unregister(ManagedSession session) {
        sessions.remove(session.id(), session);
        sessionsByWorkspace.computeIfPresent(
                session.workspaceId,
                (ignored, workspaceSessions) -> {
                    workspaceSessions.remove(session.id());
                    return workspaceSessions.isEmpty()
                            ? null
                            : workspaceSessions;
                }
        );
    }

    private final class ManagedSession implements SandboxTerminal.Session {

        private final String workspaceId;
        private final SandboxTerminal.Session delegate;
        private final java.util.concurrent.atomic.AtomicBoolean closed =
                new java.util.concurrent.atomic.AtomicBoolean();

        private ManagedSession(
                String workspaceId,
                SandboxTerminal.Session delegate
        ) {
            this.workspaceId = workspaceId;
            this.delegate = delegate;
        }

        @Override
        public String id() {
            return delegate.id();
        }

        @Override
        public reactor.core.publisher.Flux<byte[]> output() {
            return delegate.output().doFinally(ignored -> close());
        }

        @Override
        public void input(byte[] data) {
            delegate.input(data);
        }

        @Override
        public void resize(int columns, int rows) {
            delegate.resize(columns, rows);
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            closeDelegate();
            unregister(this);
        }

        private void closeDelegate() {
            delegate.close();
        }
    }
}
