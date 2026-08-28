package com.alchemist.deepexplore.harness.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alchemist.deepexplore.agent.domain.AgentProfile;
import com.alchemist.deepexplore.agent.domain.WebSearchProvider;
import com.alchemist.deepexplore.harness.application.command.ChatCommand;
import com.alchemist.deepexplore.harness.application.command.ChatStreamService;
import com.alchemist.deepexplore.harness.application.query.ToolActivityFormatter;
import com.alchemist.deepexplore.harness.domain.RunEvent;
import com.alchemist.deepexplore.harness.domain.RunEventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

class ChatControllerTest {

    private ChatStreamService chatStreamService;
    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        chatStreamService = mock(ChatStreamService.class);
        ChatEventPresenter presenter = new ChatEventPresenter(
                new ToolActivityFormatter(new ObjectMapper())
        );
        webTestClient = WebTestClient.bindToController(
                new ChatController(chatStreamService, presenter)
        ).build();
    }

    @Test
    void streamsTypedServerSentEvents() {
        when(chatStreamService.stream(any())).thenReturn(stream(
                new RunEvent.RunStarted("fast", "user-1", "assistant-1", "TAVILY"),
                new RunEvent.TextDelta("hello"),
                new RunEvent.RunCompleted("assistant-1", "model", 3)
        ));

        webTestClient.post()
                .uri("/api/chat/stream")
                .bodyValue(new ChatRequest(null, "hello"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith("text/event-stream")
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("event:metadata")
                        .contains("event:delta")
                        .contains("\"content\":\"hello\"")
                        .contains("event:done"));
    }

    @Test
    void mapsRequestFieldsToChatCommand() {
        when(chatStreamService.stream(any())).thenReturn(stream(
                new RunEvent.RunCompleted("assistant-2", "model", 3)
        ));

        webTestClient.post()
                .uri("/api/chat/stream")
                .bodyValue(new ChatRequest(
                        "conversation-1",
                        "analyze this",
                        AgentProfile.DEEP,
                        "user-2",
                        "assistant-1",
                        "assistant-2",
                        WebSearchProvider.TAVILY,
                        "workspace-1"
                ))
                .exchange()
                .expectStatus().isOk();

        ArgumentCaptor<ChatCommand> command =
                ArgumentCaptor.forClass(ChatCommand.class);
        verify(chatStreamService).stream(command.capture());
        assertThat(command.getValue().profile()).isEqualTo(AgentProfile.DEEP);
        assertThat(command.getValue().userMessageId()).isEqualTo("user-2");
        assertThat(command.getValue().userParentMessageId())
                .isEqualTo("assistant-1");
        assertThat(command.getValue().assistantMessageId())
                .isEqualTo("assistant-2");
        assertThat(command.getValue().searchProvider())
                .isEqualTo(WebSearchProvider.TAVILY);
        assertThat(command.getValue().workspaceId())
                .isEqualTo("workspace-1");
    }

    private static ChatStreamService.ChatStream stream(RunEvent... events) {
        return new ChatStreamService.ChatStream(
                WebSearchProvider.TAVILY,
                Flux.range(0, events.length)
                        .map(index -> new RunEventEnvelope(
                                "event-" + index,
                                "run-1",
                                "conversation-1",
                                "assistant",
                                index + 1L,
                                Instant.EPOCH.plusSeconds(index),
                                events[index]
                        ))
        );
    }
}
