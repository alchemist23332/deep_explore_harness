package com.alchemist.deepexplore.conversation;

public interface ConversationStore {

    ConversationTurn beginTurn(String conversationId, String userMessage);

    void completeTurn(String conversationId, String assistantMessage);

    void failTurn(String conversationId);
}
