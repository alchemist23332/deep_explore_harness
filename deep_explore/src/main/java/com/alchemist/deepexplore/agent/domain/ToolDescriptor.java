package com.alchemist.deepexplore.agent.domain;

public record ToolDescriptor(
        String name,
        String displayName,
        ArgumentExposure argumentExposure,
        ResultExposure resultExposure
) {

    public enum ArgumentExposure {
        NONE,
        DESCRIPTION,
        QUERY
    }

    public enum ResultExposure {
        NONE,
        SUMMARY
    }
}
