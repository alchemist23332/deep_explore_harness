package com.alchemist.deepexplore.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentInvocationContextRegistryTest {

    @Test
    void bindsAndClearsOnlyTheMatchingRun() {
        AgentInvocationContextRegistry registry =
                new AgentInvocationContextRegistry();

        registry.bind("conversation-1", "run-1", "workspace-1");
        registry.clear("conversation-1", "other-run");

        AgentInvocationContextRegistry.Context context =
                registry.require("conversation-1");
        assertThat(context.runId()).isEqualTo("run-1");
        assertThat(context.workspaceId()).isEqualTo("workspace-1");
        assertThat(context.increment("tools")).isEqualTo(1);
        assertThat(context.increment("tools")).isEqualTo(2);

        registry.clear("conversation-1", "run-1");

        assertThat(registry.find("conversation-1")).isEmpty();
    }

    @Test
    void blankWorkspaceRemovesExistingBinding() {
        AgentInvocationContextRegistry registry =
                new AgentInvocationContextRegistry();
        registry.bind("conversation-1", "run-1", "workspace-1");

        registry.bind("conversation-1", "run-2", " ");

        assertThat(registry.isBound("conversation-1")).isFalse();
    }
}
