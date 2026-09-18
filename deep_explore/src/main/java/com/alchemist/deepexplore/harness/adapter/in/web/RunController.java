package com.alchemist.deepexplore.harness.adapter.in.web;

import com.alchemist.deepexplore.harness.application.command.ChatStreamService;
import com.alchemist.deepexplore.harness.application.execution.RunExecutionManager;
import com.alchemist.deepexplore.harness.domain.AgentRun;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/runs")
public class RunController {

    private final ChatStreamService chatCommands;
    private final RunExecutionManager runs;
    private final ChatEventPresenter presenter;

    public RunController(
            ChatStreamService chatCommands,
            RunExecutionManager runs,
            ChatEventPresenter presenter
    ) {
        this.chatCommands = chatCommands;
        this.runs = runs;
        this.presenter = presenter;
    }

    @PostMapping
    public Mono<ResponseEntity<RunResponse>> start(
            @Valid @RequestBody ChatRequest request
    ) {
        return runs.start(chatCommands.toStartRun(request.toCommand()))
                .map(started -> ResponseEntity.accepted().body(
                        new RunResponse(
                                started.runId(),
                                started.conversationId(),
                                started.assistantMessageId(),
                                "RUNNING"
                        )
                ));
    }

    @GetMapping("/{runId}")
    public Mono<RunResponse> get(@PathVariable String runId) {
        return runs.get(runId).map(RunResponse::from);
    }

    @GetMapping(
            value = "/{runId}/events",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    public Flux<ServerSentEvent<ChatStreamEvent>> events(
            @PathVariable String runId,
            @RequestParam(defaultValue = "0") long afterSequence,
            @RequestHeader(
                    name = "Last-Event-ID",
                    required = false
            ) String lastEventId
    ) {
        long cursor = Math.max(afterSequence, parseSequence(lastEventId));
        return runs.searchProvider(runId)
                .flatMapMany(provider -> runs.events(runId, cursor)
                        .mapNotNull(event -> presenter.present(event, provider))
                        .map(event -> ServerSentEvent
                                .<ChatStreamEvent>builder(event)
                                .id(Long.toString(event.sequence()))
                                .event(event.type())
                                .build()));
    }

    @PostMapping("/{runId}/cancel")
    public Mono<ResponseEntity<Void>> cancel(@PathVariable String runId) {
        return runs.cancel(runId).map(cancelled -> cancelled
                ? ResponseEntity.accepted().build()
                : ResponseEntity.noContent().build());
    }

    private static long parseSequence(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    public record RunResponse(
            String runId,
            String conversationId,
            String assistantMessageId,
            String status
    ) {

        static RunResponse from(AgentRun run) {
            return new RunResponse(
                    run.id(),
                    run.conversationId(),
                    run.assistantMessageId(),
                    run.status().name()
            );
        }
    }
}
