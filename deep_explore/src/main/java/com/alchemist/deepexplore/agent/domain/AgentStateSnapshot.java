package com.alchemist.deepexplore.agent.domain;

public record AgentStateSnapshot(String payload) {

    public static AgentStateSnapshot empty() {
        return new AgentStateSnapshot("[]");
    }
}
