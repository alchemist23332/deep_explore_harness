package com.alchemist.deepexplore.conversation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InMemoryConversationStore implements ConversationStore {

    private final Map<String, ConversationState> conversations = new ConcurrentHashMap<>();
    private final int maxMessages;

    public InMemoryConversationStore(@Value("${app.conversation.max-messages:20}") int maxMessages) {
        this.maxMessages = maxMessages;
    }

    @Override
    public ConversationTurn beginTurn(String requestedId, String userMessage) {
        String conversationId = requestedId == null || requestedId.isBlank()
                ? UUID.randomUUID().toString()
                : requestedId;
        ConversationState state = conversations.computeIfAbsent(
                conversationId,
                ignored -> new ConversationState()
        );

        synchronized (state) {
            if (state.inFlight) {
                throw new IllegalStateException("该会话正在生成回复，请等待当前请求完成");
            }
            state.inFlight = true;
            state.messages.addLast(new ConversationMessage(
                    ConversationMessage.Role.USER,
                    userMessage
            ));
            trim(state.messages);
            return new ConversationTurn(conversationId, new ArrayList<>(state.messages));
        }
    }

    @Override
    public void completeTurn(String conversationId, String assistantMessage) {
        ConversationState state = conversations.get(conversationId);
        if (state == null) {
            return;
        }

        synchronized (state) {
            state.messages.addLast(new ConversationMessage(
                    ConversationMessage.Role.ASSISTANT,
                    assistantMessage
            ));
            trim(state.messages);
            state.inFlight = false;
        }
    }

    @Override
    public void failTurn(String conversationId) {
        ConversationState state = conversations.get(conversationId);
        if (state == null) {
            return;
        }

        synchronized (state) {
            if (!state.messages.isEmpty()
                    && state.messages.getLast().role() == ConversationMessage.Role.USER) {
                state.messages.removeLast();
            }
            state.inFlight = false;
        }
    }

    private void trim(Deque<ConversationMessage> messages) {
        while (messages.size() > maxMessages) {
            messages.removeFirst();
        }
    }

    private static final class ConversationState {
        private final Deque<ConversationMessage> messages = new ArrayDeque<>();
        private boolean inFlight;
    }
}
