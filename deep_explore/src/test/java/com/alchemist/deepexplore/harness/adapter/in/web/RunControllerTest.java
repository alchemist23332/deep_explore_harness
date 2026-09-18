package com.alchemist.deepexplore.harness.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.alchemist.deepexplore.agent.application.ToolDescriptorTestFixtures;
import com.alchemist.deepexplore.harness.application.command.ChatStreamService;
import com.alchemist.deepexplore.harness.application.command.StartRunCommand;
import com.alchemist.deepexplore.harness.application.execution.RunExecutionManager;
import com.alchemist.deepexplore.harness.application.query.ToolActivityFormatter;
import com.alchemist.deepexplore.harness.domain.RunEvent;
import com.alchemist.deepexplore.harness.domain.RunEventEnvelope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class RunControllerTest {

    private ChatStreamService commands;
    private RunExecutionManager runs;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        commands = mock(ChatStreamService.class);
        runs = mock(RunExecutionManager.class);
        ChatEventPresenter presenter = new ChatEventPresenter(
                new ToolActivityFormatter(
                        new ObjectMapper(),
                        ToolDescriptorTestFixtures.registry()
                )
        );
        client = WebTestClient.bindToController(
                new RunController(commands, runs, presenter)
        ).build();
    }

    @Test
    void startsRunAndReplaysEventsByRunId() {
        StartRunCommand command = new StartRunCommand(
                "conversation-1",
                "hello",
                "assistant",
                "fast",
                "user-1",
                null,
                "assistant-1"
        );
        when(commands.toStartRun(any())).thenReturn(command);
        when(runs.start(command)).thenReturn(Mono.just(
                new RunExecutionManager.StartedRun(
                        "run-1",
                        "conversation-1",
                        "assistant-1"
                )
        ));
        when(runs.searchProvider("run-1")).thenReturn(Mono.just("TAVILY"));
        when(runs.events("run-1", 0)).thenReturn(Flux.just(
                envelope(1, new RunEvent.RunStarted(
                        "fast",
                        "user-1",
                        "assistant-1",
                        "TAVILY"
                )),
                envelope(2, new RunEvent.TextDelta("hello")),
                envelope(3, new RunEvent.RunCompleted(
                        "assistant-1",
                        "model",
                        3
                ))
        ));

        client.post()
                .uri("/api/runs")
                .bodyValue(new ChatRequest(null, "hello"))
                .exchange()
                .expectStatus().isAccepted()
                .expectBody()
                .jsonPath("$.runId").isEqualTo("run-1");

        client.get()
                .uri("/api/runs/run-1/events")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> assertThat(body)
                        .contains("event:metadata")
                        .contains("event:delta")
                        .contains("event:done"));
    }

    private static RunEventEnvelope envelope(long sequence, RunEvent event) {
        return new RunEventEnvelope(
                "event-" + sequence,
                "run-1",
                "conversation-1",
                "assistant",
                sequence,
                Instant.EPOCH.plusSeconds(sequence),
                event
        );
    }
}
