package com.alchemist.deepexplore.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alchemist.deepexplore.agent.AgentCommand;
import com.alchemist.deepexplore.agent.AgentEvent;
import com.alchemist.deepexplore.agent.AgentMode;
import com.alchemist.deepexplore.agent.AgentService;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

class ChatControllerTest {

    private AgentService agentService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        agentService = mock(AgentService.class);
        webTestClient = WebTestClient.bindToController(new ChatController(agentService)).build();
    }

    @Test
    void streamsTypedServerSentEvents() {
        when(agentService.stream(any())).thenReturn(Flux.just(
                AgentEvent.metadata("conversation-1"),
                AgentEvent.delta("conversation-1", "hello"),
                AgentEvent.done("conversation-1")
        ));

        webTestClient.post()
                .uri("/api/chat/stream")
                .bodyValue(new ChatRequest(null, "hello"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith("text/event-stream")
                .expectBody(String.class)
                .value(body -> {
                    org.assertj.core.api.Assertions.assertThat(body)
                            .contains("event:metadata")
                            .contains("event:delta")
                            .contains("\"content\":\"hello\"")
                            .contains("event:done");
                });
    }

    @Test
    void passesSelectedDeepModeToAgentService() {
        when(agentService.stream(any())).thenReturn(Flux.just(AgentEvent.done("conversation-1")));

        webTestClient.post()
                .uri("/api/chat/stream")
                .bodyValue(new ChatRequest(null, "analyze this", AgentMode.DEEP))
                .exchange()
                .expectStatus().isOk();

        ArgumentCaptor<AgentCommand> command = ArgumentCaptor.forClass(AgentCommand.class);
        verify(agentService).stream(command.capture());
        org.assertj.core.api.Assertions.assertThat(command.getValue().mode())
                .isEqualTo(AgentMode.DEEP);
    }
}
