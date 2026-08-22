package com.alchemist.deepexplore.workspace.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alchemist.deepexplore.workspace.port.SandboxTerminal;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class TerminalSessionRegistryTest {

    private static final String WORKSPACE_ID = "workspace-1";
    private static final String CONTAINER_ID = "container-1";

    @Test
    void enforcesLimitAndClosesWorkspaceSessions() {
        SandboxTerminal terminal = mock(SandboxTerminal.class);
        SandboxTerminal.Session first = session("terminal-1");
        SandboxTerminal.Session second = session("terminal-2");
        SandboxTerminal.Session third = session("terminal-3");
        when(terminal.open(
                anyString(),
                anyString(),
                anyString(),
                anyInt(),
                anyInt()
        )).thenReturn(first, second, third);
        TerminalSessionRegistry registry = new TerminalSessionRegistry(
                terminal
        );

        registry.open(WORKSPACE_ID, CONTAINER_ID, 100, 30);
        registry.open(WORKSPACE_ID, CONTAINER_ID, 100, 30);
        registry.open(WORKSPACE_ID, CONTAINER_ID, 100, 30);

        assertThatThrownBy(() -> registry.open(
                WORKSPACE_ID,
                CONTAINER_ID,
                100,
                30
        ))
                .isInstanceOf(WorkspaceOperationException.class)
                .hasMessageContaining("limit");

        registry.closeWorkspace(WORKSPACE_ID);

        verify(first).close();
        verify(second).close();
        verify(third).close();
    }

    @Test
    void unregistersSessionWhenOutputCompletes() {
        SandboxTerminal terminal = mock(SandboxTerminal.class);
        SandboxTerminal.Session delegate = session("terminal-1");
        when(delegate.output()).thenReturn(Flux.empty());
        when(terminal.open(
                anyString(),
                anyString(),
                anyString(),
                anyInt(),
                anyInt()
        )).thenReturn(delegate);
        TerminalSessionRegistry registry = new TerminalSessionRegistry(
                terminal
        );
        SandboxTerminal.Session managed = registry.open(
                WORKSPACE_ID,
                CONTAINER_ID,
                100,
                30
        );

        StepVerifier.create(managed.output()).verifyComplete();

        verify(delegate).close();
    }

    private static SandboxTerminal.Session session(String id) {
        SandboxTerminal.Session session = mock(SandboxTerminal.Session.class);
        when(session.id()).thenReturn(id);
        when(session.output()).thenReturn(Flux.never());
        return session;
    }
}
