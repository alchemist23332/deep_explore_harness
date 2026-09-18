package com.alchemist.deepexplore.harness.application.execution;

public class RunNotFoundException extends RuntimeException {

    public RunNotFoundException(String runId) {
        super("Run not found: " + runId);
    }
}
