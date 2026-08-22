package com.alchemist.deepexplore.agent.domain;

public enum AgentProfile {
    FAST("fast"),
    DEEP("deep");

    private final String id;

    AgentProfile(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }
}
