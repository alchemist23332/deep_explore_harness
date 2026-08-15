package com.alchemist.deepexplore.agent;

public record AgentCommand(
        String conversationId,
        String message,
        AgentMode mode,
        String userMessageId,
        String userParentMessageId,
        String assistantMessageId
) {

    public AgentCommand(String conversationId, String message) {
        this(conversationId, message, AgentMode.FAST, null, null, null);
    }

    public AgentCommand(String conversationId, String message, AgentMode mode) {
        this(conversationId, message, mode, null, null, null);
    }

    public AgentCommand {
        mode = mode == null ? AgentMode.FAST : mode;
    }
}
