package com.alchemist.deepexplore.workspace.adapter.in.web;

import com.alchemist.deepexplore.workspace.application.terminal.TerminalApplicationService;
import com.alchemist.deepexplore.workspace.application.terminal.TerminalConnection;
import com.alchemist.deepexplore.support.BlockingExecution;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class TerminalWebSocketHandler implements WebSocketHandler {

    private final TerminalApplicationService terminals;
    private final ObjectMapper objectMapper;
    private final BlockingExecution blocking;

    public TerminalWebSocketHandler(
            TerminalApplicationService terminals,
            ObjectMapper objectMapper,
            BlockingExecution blocking
    ) {
        this.terminals = terminals;
        this.objectMapper = objectMapper;
        this.blocking = blocking;
    }

    @Override
    public Mono<Void> handle(WebSocketSession webSocket) {
        String workspaceId = workspaceId(
                webSocket.getHandshakeInfo().getUri()
        );
        return blocking.mono(
                BlockingExecution.Kind.DOCKER,
                () -> terminals.open(workspaceId, 100, 30)
        ).flatMap(terminal -> handleConnected(webSocket, terminal));
    }

    private Mono<Void> handleConnected(
            WebSocketSession webSocket,
            TerminalConnection terminal
    ) {
        Flux<WebSocketMessage> outbound = Flux.concat(
                Mono.fromSupplier(() -> webSocket.textMessage(
                        json(Map.of(
                                "type", "ready",
                                "sessionId", terminal.id()
                        ))
                )),
                terminal.output().map(bytes -> {
                    DataBuffer buffer = webSocket.bufferFactory().wrap(bytes);
                    return webSocket.binaryMessage(ignored -> buffer);
                }),
                Mono.fromSupplier(() -> webSocket.textMessage(
                        json(Map.of("type", "exit"))
                ))
        ).onErrorResume(error -> Flux.just(webSocket.textMessage(
                json(Map.of(
                        "type", "error",
                        "message", safeMessage(error)
                ))
        )));

        Mono<Void> sender = webSocket.send(outbound);
        Mono<Void> receiver = webSocket.receive()
                .concatMap(message -> blocking.mono(
                        BlockingExecution.Kind.DOCKER,
                        () -> {
                            handleMessage(terminal, message);
                            return true;
                        }
                ))
                .then();

        return Mono.firstWithSignal(sender, receiver)
                .doFinally(ignored -> terminal.close())
                .then();
    }

    private void handleMessage(
            TerminalConnection terminal,
            WebSocketMessage message
    ) {
        try {
            if (message.getType() == WebSocketMessage.Type.BINARY) {
                byte[] data = new byte[message.getPayload().readableByteCount()];
                message.getPayload().read(data);
                terminal.input(data);
                return;
            }
            TerminalClientMessage clientMessage = objectMapper.readValue(
                    message.getPayloadAsText(),
                    TerminalClientMessage.class
            );
            switch (clientMessage.type()) {
                case "input" -> terminal.input(
                        value(clientMessage.data())
                                .getBytes(StandardCharsets.UTF_8)
                );
                case "resize" -> terminal.resize(
                        clientMessage.columns() == null
                                ? 100
                                : clientMessage.columns(),
                        clientMessage.rows() == null
                                ? 30
                                : clientMessage.rows()
                );
                default -> {
                    // Unknown client events are ignored for forward compatibility.
                }
            }
        } catch (Exception error) {
            throw new IllegalArgumentException(
                    "Invalid terminal WebSocket message",
                    error
            );
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
            throw new IllegalStateException(
                    "Unable to serialize terminal event",
                    error
            );
        }
    }

    private static String workspaceId(URI uri) {
        String[] segments = uri.getPath().split("/");
        for (int index = 0; index < segments.length - 1; index++) {
            if (segments[index].equals("workspaces")
                    && !segments[index + 1].isBlank()) {
                return segments[index + 1];
            }
        }
        throw new IllegalArgumentException(
                "Terminal URL does not contain a workspace id"
        );
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null
                ? "Terminal session failed"
                : error.getMessage();
    }

    private record TerminalClientMessage(
            String type,
            String data,
            Integer columns,
            Integer rows
    ) {
    }
}
