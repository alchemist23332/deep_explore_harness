package com.alchemist.deepexplore.conversation;

public record ConversationMessage(Role role, String content) {

    public enum Role {
        USER,
        ASSISTANT
    }
}
