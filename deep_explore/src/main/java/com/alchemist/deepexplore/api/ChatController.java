package com.alchemist.deepexplore.api;

import java.util.Map;

import com.alchemist.deepexplore.agent.AgentCommand;
import com.alchemist.deepexplore.agent.AgentEvent;
import com.alchemist.deepexplore.agent.AgentService;
import jakarta.validation.Valid;
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

    private final AgentService agentService;

    public ChatController(AgentService agentService) {
        this.agentService = agentService;
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
    public Flux<ServerSentEvent<AgentEvent>> stream(@Valid @RequestBody ChatRequest request) {
        AgentCommand command = new AgentCommand(
                request.conversationId(),
                request.message(),
                request.mode()
        );
        return agentService.stream(command)
                .map(event -> ServerSentEvent.<AgentEvent>builder(event)
                        .event(event.type())
                        .build());
    }
}
