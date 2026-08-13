package com.alchemist.deepexplore.agent;

public record AgentEvent(String type, String conversationId, String content) {

    public static AgentEvent metadata(String conversationId) {
        return new AgentEvent("metadata", conversationId, "");
    }

    public static AgentEvent delta(String conversationId, String content) {
        return new AgentEvent("delta", conversationId, content);
    }

    public static AgentEvent done(String conversationId) {
        return new AgentEvent("done", conversationId, "");
    }

    public static AgentEvent error(String conversationId, String message) {
        return new AgentEvent("error", conversationId, message);
    }
}
