package com.alchemist.deepexplore.workspace.application.terminal;

import reactor.core.publisher.Flux;

public interface TerminalConnection extends AutoCloseable {

    String id();

    Flux<byte[]> output();

    void input(byte[] data);

    void resize(int columns, int rows);

    @Override
    void close();
}
