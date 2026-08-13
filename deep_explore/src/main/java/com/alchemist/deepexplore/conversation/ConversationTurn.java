package com.alchemist.deepexplore.conversation;

import java.util.List;

public record ConversationTurn(String conversationId, List<ConversationMessage> messages) {
}
