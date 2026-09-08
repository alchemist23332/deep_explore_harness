package com.alchemist.deepexplore.conversation.adapter.in.web;

import com.alchemist.deepexplore.conversation.application.ConversationApplicationService;
import com.alchemist.deepexplore.conversation.domain.Conversation;
import com.alchemist.deepexplore.support.BlockingExecution;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationApplicationService conversations;
    private final BlockingExecution blocking;

    public ConversationController(
            ConversationApplicationService conversations,
            BlockingExecution blocking
    ) {
        this.conversations = conversations;
        this.blocking = blocking;
    }

    @GetMapping
    public Mono<List<ConversationWebModels.Response>> list() {
        return blocking(() -> conversations.list().stream()
                .map(ConversationWebModels.Response::from)
                .toList());
    }

    @PostMapping
    public Mono<ConversationWebModels.Response> create() {
        return blocking(() -> ConversationWebModels.Response.from(
                conversations.create()
        ));
    }

    @GetMapping("/{conversationId}")
    public Mono<ConversationWebModels.Response> get(
            @PathVariable String conversationId
    ) {
        return blocking(() -> ConversationWebModels.Response.from(
                conversations.get(conversationId)
        ));
    }

    @PatchMapping("/{conversationId}")
    public Mono<ConversationWebModels.Response> update(
            @PathVariable String conversationId,
            @Valid @RequestBody ConversationWebModels.PatchRequest request
    ) {
        return blocking(() -> ConversationWebModels.Response.from(
                conversations.update(
                        conversationId,
                        request.title(),
                        request.status()
                )
        ));
    }

    @DeleteMapping("/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable String conversationId) {
        return blocking(() -> {
            conversations.delete(conversationId);
            return (Void) null;
        });
    }

    @GetMapping("/{conversationId}/messages")
    public Mono<ConversationWebModels.MessagesResponse> messages(
            @PathVariable String conversationId
    ) {
        return blocking(() -> {
            Conversation conversation = conversations.get(conversationId);
            return new ConversationWebModels.MessagesResponse(
                    conversation.headMessageId(),
                    conversations.messages(conversationId).stream()
                            .map(ConversationWebModels.MessageResponse::from)
                            .toList()
            );
        });
    }

    @PostMapping("/import")
    public Mono<Void> importLocalHistory(
            @Valid @RequestBody ConversationWebModels.ImportRequest request
    ) {
        return blocking(() -> {
            conversations.importHistory(request.toCommand());
            return (Void) null;
        });
    }

    private <T> Mono<T> blocking(java.util.concurrent.Callable<T> action) {
        return blocking.mono(BlockingExecution.Kind.JDBC, action);
    }
}
