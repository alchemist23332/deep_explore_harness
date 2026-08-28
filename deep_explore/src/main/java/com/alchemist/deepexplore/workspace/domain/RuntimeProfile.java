package com.alchemist.deepexplore.workspace.domain;

public enum RuntimeProfile {
    FULLSTACK(
            "Fullstack",
            "Java 21 + Maven + Node.js 22 + pnpm"
    );

    private final String displayName;
    private final String description;

    RuntimeProfile(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }
}
