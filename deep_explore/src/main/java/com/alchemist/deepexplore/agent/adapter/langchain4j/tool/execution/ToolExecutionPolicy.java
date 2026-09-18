package com.alchemist.deepexplore.agent.adapter.langchain4j.tool.execution;

import java.util.Objects;

public record ToolExecutionPolicy(
        ToolEffect effect,
        ToolResource resource
) {

    public ToolExecutionPolicy {
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(resource, "resource");
    }

    public static ToolExecutionPolicy readOnly(ToolResource resource) {
        return new ToolExecutionPolicy(ToolEffect.READ_ONLY, resource);
    }

    public static ToolExecutionPolicy mutation(ToolResource resource) {
        return new ToolExecutionPolicy(
                ToolEffect.WORKSPACE_MUTATION,
                resource
        );
    }

    public static ToolExecutionPolicy exclusive(ToolResource resource) {
        return new ToolExecutionPolicy(ToolEffect.EXCLUSIVE, resource);
    }

    public boolean canRunConcurrently() {
        return effect == ToolEffect.READ_ONLY;
    }
}
