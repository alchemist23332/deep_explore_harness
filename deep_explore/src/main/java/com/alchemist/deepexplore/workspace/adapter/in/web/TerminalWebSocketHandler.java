package com.alchemist.deepexplore.workspace.adapter.in.web;

import com.alchemist.deepexplore.workspace.application.terminal.TerminalApplicationService;
import com.alchemist.deepexplore.workspace.application.terminal.TerminalConnection;
import com.alchemist.deepexplore.support.BlockingExecution;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class TerminalWebSocketHandler implements WebSocketHandler {

    private static final Logger log =
            LoggerFactory.getLogger(TerminalWebSocketHandler.class);

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
                .concatMap(message -> {
                    TerminalClientFrame frame = snapshot(message);
                    return blocking.mono(
                            BlockingExecution.Kind.DOCKER,
                            () -> {
                                handleMessage(terminal, frame);
                                return true;
                            }
                    );
                })
                .doOnError(error -> log.warn(
                        "Terminal WebSocket receive failed for session {}",
                        terminal.id(),
                        error
                ))
                .then();

        return Mono.firstWithSignal(sender, receiver)
                .doFinally(ignored -> terminal.close())
                .then();
    }

    private void handleMessage(
            TerminalConnection terminal,
            TerminalClientFrame message
    ) {
        try {
            if (message.binary() != null) {
                terminal.input(message.binary());
                return;
            }
            TerminalClientMessage clientMessage = objectMapper.readValue(
                    message.text(),
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

    /**
     * Inbound WebSocket message buffers are pooled and only valid for the
     * duration of {@code onNext}. Extract the payload eagerly on the event
     * loop; the blocking pool thread must not touch the Netty buffer later
     * (it would hit {@code IllegalReferenceCountException: refCnt: 0}).
     */
    private static TerminalClientFrame snapshot(WebSocketMessage message) {
        if (message.getType() == WebSocketMessage.Type.BINARY) {
            var payload = message.getPayload();
            byte[] data = new byte[payload.readableByteCount()];
            payload.read(data);
            return new TerminalClientFrame(null, data);
        }
        return new TerminalClientFrame(message.getPayloadAsText(), null);
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

    private record TerminalClientFrame(String text, byte[] binary) {
    }

    private record TerminalClientMessage(
            String type,
            String data,
            Integer columns,
            Integer rows
    ) {
    }
}
