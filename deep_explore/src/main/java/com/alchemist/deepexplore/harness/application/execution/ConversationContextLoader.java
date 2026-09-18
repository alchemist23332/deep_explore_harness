package com.alchemist.deepexplore.harness.application.execution;

import com.alchemist.deepexplore.agent.domain.AgentMessage;
import com.alchemist.deepexplore.conversation.domain.Conversation;
import com.alchemist.deepexplore.conversation.domain.ConversationMessage;
import com.alchemist.deepexplore.conversation.port.ConversationStore;
import com.alchemist.deepexplore.conversation.port.MessageStore;
import com.alchemist.deepexplore.harness.application.command.StartRunCommand;
import com.alchemist.deepexplore.harness.domain.AgentRun;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class ConversationContextLoader {

    private final ConversationStore conversations;
    private final MessageStore messages;
    private final int maxMessages;

    ConversationContextLoader(
            ConversationStore conversations,
            MessageStore messages,
            @Value("${app.conversation.max-messages:20}") int maxMessages
    ) {
        this.conversations = conversations;
        this.messages = messages;
        this.maxMessages = maxMessages;
    }

    Conversation open(StartRunCommand command) {
        return conversations.create(command.conversationId(), null);
    }

    Context load(
            Conversation conversation,
            StartRunCommand command,
            String userMessageId
    ) {
        boolean replay = messages.exists(
                conversation.id(),
                userMessageId
        );
        String headMessageId = replay
                ? command.userParentMessageId()
                : conversation.headMessageId();
        List<AgentMessage> history = messages.branch(
                        conversation.id(),
                        headMessageId,
                        maxMessages
                ).stream()
                .map(message -> new AgentMessage(
                        message.role() == ConversationMessage.Role.USER
                                ? AgentMessage.Role.USER
                                : AgentMessage.Role.ASSISTANT,
                        message.content()
                ))
                .toList();
        return new Context(conversation, replay, headMessageId, history);
    }

    void appendUserMessage(
            AgentRun run,
            StartRunCommand command
    ) {
        messages.append(
                run.conversationId(),
                run.userMessageId(),
                command.userParentMessageId(),
                ConversationMessage.Role.USER,
                command.message(),
                ConversationMessage.Status.COMPLETE,
                null,
                null
        );
    }

    record Context(
            Conversation conversation,
            boolean replay,
            String headMessageId,
            List<AgentMessage> history
    ) {
    }
}
