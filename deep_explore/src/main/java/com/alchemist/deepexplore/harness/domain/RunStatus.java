package com.alchemist.deepexplore.harness.domain;

public enum RunStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    WAITING_APPROVAL;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED;
    }
}
