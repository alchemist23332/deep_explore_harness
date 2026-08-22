package com.alchemist.deepexplore.agent.domain;

public record AgentMessage(Role role, String content) {

    public enum Role {
        USER,
        ASSISTANT
    }
}
