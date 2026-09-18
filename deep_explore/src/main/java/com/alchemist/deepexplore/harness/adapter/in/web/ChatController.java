package com.alchemist.deepexplore.harness.adapter.in.web;

import com.alchemist.deepexplore.harness.application.command.ChatStreamService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
public class ChatController {

    private final ChatStreamService chatStreamService;
    private final ChatEventPresenter presenter;

    public ChatController(
            ChatStreamService chatStreamService,
            ChatEventPresenter presenter
    ) {
        this.chatStreamService = chatStreamService;
        this.presenter = presenter;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @PostMapping(
            value = "/chat/stream",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<ChatStreamEvent>> stream(
            @Valid @RequestBody ChatRequest request
    ) {
        ChatStreamService.ChatStream stream = chatStreamService.stream(
                request.toCommand()
        );
        String provider = stream.searchProvider().name();
        return stream.events()
                .mapNotNull(event -> presenter.present(event, provider))
                .map(event -> ServerSentEvent.<ChatStreamEvent>builder(event)
                        .event(event.type())
                        .build());
    }
}
