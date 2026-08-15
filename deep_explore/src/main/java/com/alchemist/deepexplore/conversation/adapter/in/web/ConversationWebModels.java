package com.alchemist.deepexplore.conversation.adapter.in.web;

import com.alchemist.deepexplore.conversation.application.ConversationImport;
import com.alchemist.deepexplore.conversation.domain.Conversation;
import com.alchemist.deepexplore.conversation.domain.ConversationMessage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class ConversationWebModels {

    private ConversationWebModels() {
    }

    public record Response(
            String id,
            String title,
            Conversation.Status status,
            String headMessageId,
            Instant createdAt,
            Instant updatedAt
    ) {
        static Response from(Conversation conversation) {
            return new Response(
                    conversation.id(),
                    conversation.title(),
                    conversation.status(),
                    conversation.headMessageId(),
                    conversation.createdAt(),
                    conversation.updatedAt()
            );
        }
    }

    public record PatchRequest(
            @Size(max = 80) String title,
            Conversation.Status status
    ) {
    }

    public record MessagesResponse(
            String headId,
            List<MessageResponse> messages
    ) {
    }

    public record MessageResponse(
            String id,
            String parentMessageId,
            ConversationMessage.Role role,
            String content,
            ConversationMessage.Status status,
            Instant createdAt
    ) {
        static MessageResponse from(ConversationMessage message) {
            return new MessageResponse(
                    message.id(),
                    message.parentMessageId(),
                    message.role(),
                    message.content(),
                    message.status(),
                    message.createdAt()
            );
        }
    }

    public record ImportRequest(
            List<@Valid ImportedConversation> conversations
    ) {
        public ImportRequest {
            conversations = conversations == null ? List.of() : List.copyOf(conversations);
        }

        List<ConversationImport> toCommand() {
            return conversations.stream()
                    .map(ImportedConversation::toCommand)
                    .toList();
        }
    }

    public record ImportedConversation(
            @NotBlank @Size(max = 100) String id,
            @Size(max = 80) String title,
            Conversation.Status status,
            List<@Valid ImportedMessage> messages
    ) {
        public ImportedConversation {
            messages = messages == null ? List.of() : List.copyOf(messages);
        }

        ConversationImport toCommand() {
            return new ConversationImport(
                    id,
                    title,
                    status,
                    messages.stream().map(ImportedMessage::toCommand).toList()
            );
        }
    }

    public record ImportedMessage(
            @NotBlank @Size(max = 100) String id,
            @Size(max = 100) String parentMessageId,
            @NotNull ConversationMessage.Role role,
            @NotBlank @Size(max = 20_000) String content,
            ConversationMessage.Status status,
            Instant createdAt
    ) {
        public ImportedMessage {
            createdAt = createdAt == null ? Instant.EPOCH : createdAt;
        }

        ConversationImport.Message toCommand() {
            return new ConversationImport.Message(
                    id,
                    parentMessageId,
                    role,
                    content,
                    status,
                    createdAt
            );
        }
    }
}
