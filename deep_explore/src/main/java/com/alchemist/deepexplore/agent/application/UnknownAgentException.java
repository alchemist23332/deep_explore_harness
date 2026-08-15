package com.alchemist.deepexplore.agent.application;

public class UnknownAgentException extends RuntimeException {

    public UnknownAgentException(String agentId) {
        super("Unknown agent: " + agentId);
    }
}
