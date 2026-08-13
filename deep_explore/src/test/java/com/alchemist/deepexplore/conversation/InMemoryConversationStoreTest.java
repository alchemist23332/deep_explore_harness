package com.alchemist.deepexplore.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InMemoryConversationStoreTest {

    private final InMemoryConversationStore store = new InMemoryConversationStore(4);

    @Test
    void preservesCompletedConversationHistory() {
        ConversationTurn firstTurn = store.beginTurn(null, "hello");
        store.completeTurn(firstTurn.conversationId(), "hi");

        ConversationTurn secondTurn = store.beginTurn(firstTurn.conversationId(), "next");

        assertThat(secondTurn.messages())
                .extracting(ConversationMessage::content)
                .containsExactly("hello", "hi", "next");
    }

    @Test
    void rollsBackUserMessageWhenGenerationFails() {
        ConversationTurn firstTurn = store.beginTurn("conversation-1", "failed message");
        store.failTurn(firstTurn.conversationId());

        ConversationTurn retry = store.beginTurn("conversation-1", "retry");

        assertThat(retry.messages())
                .extracting(ConversationMessage::content)
                .containsExactly("retry");
    }

    @Test
    void rejectsConcurrentTurnsForSameConversation() {
        store.beginTurn("conversation-1", "first");

        assertThatThrownBy(() -> store.beginTurn("conversation-1", "second"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("正在生成回复");
    }
}
