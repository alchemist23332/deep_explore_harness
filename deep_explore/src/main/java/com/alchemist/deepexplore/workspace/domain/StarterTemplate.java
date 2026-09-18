package com.alchemist.deepexplore.workspace.domain;

public enum StarterTemplate {
    WEB_TYPESCRIPT("Web TypeScript"),
    JAVA_MAVEN("Java Maven"),
    EMPTY("Empty");

    private final String displayName;

    StarterTemplate(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
