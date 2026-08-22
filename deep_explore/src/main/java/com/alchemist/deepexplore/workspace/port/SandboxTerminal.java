package com.alchemist.deepexplore.workspace.port;

import reactor.core.publisher.Flux;

public interface SandboxTerminal {

    Session open(
            String workspaceId,
            String containerId,
            String workingDirectory,
            int columns,
            int rows
    );

    interface Session extends AutoCloseable {

        String id();

        Flux<byte[]> output();

        void input(byte[] data);

        void resize(int columns, int rows);

        @Override
        void close();
    }
}
