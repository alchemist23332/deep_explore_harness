package com.alchemist.deepexplore.workspace.domain;

public enum RuntimeProfile {
    JAVA_21("Java 21", "JDK 21 + Maven");

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
