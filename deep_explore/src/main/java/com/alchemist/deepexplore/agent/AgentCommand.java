package com.alchemist.deepexplore.agent;

public record AgentCommand(String conversationId, String message, AgentMode mode) {

    public AgentCommand(String conversationId, String message) {
        this(conversationId, message, AgentMode.FAST);
    }

    public AgentCommand {
        mode = mode == null ? AgentMode.FAST : mode;
    }
}
