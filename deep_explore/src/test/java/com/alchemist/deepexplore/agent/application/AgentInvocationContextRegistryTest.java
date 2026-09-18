package com.alchemist.deepexplore.agent.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AgentInvocationContextRegistryTest {

    @Test
    void bindsAndClearsOnlyTheMatchingRun() {
        AgentInvocationContextRegistry registry =
                new AgentInvocationContextRegistry();

        registry.bind("run-1", "conversation-1", "workspace-1");
        registry.clear("other-run");

        AgentInvocationContextRegistry.Context context =
                registry.require("run-1");
        assertThat(context.conversationId()).isEqualTo("conversation-1");
        assertThat(context.workspaceId()).isEqualTo("workspace-1");
        assertThat(context.increment("tools")).isEqualTo(1);
        assertThat(context.increment("tools")).isEqualTo(2);

        registry.clear("run-1");

        assertThat(registry.find("run-1")).isEmpty();
    }

    @Test
    void blankWorkspaceHidesToolsButRetainsRunOwnership() {
        AgentInvocationContextRegistry registry =
                new AgentInvocationContextRegistry();
        registry.bind("run-1", "conversation-1", "workspace-1");

        registry.bind("run-2", "conversation-1", " ");

        assertThat(registry.isBound("run-2")).isFalse();
        assertThat(registry.conversationId("run-2"))
                .contains("conversation-1");
        assertThat(registry.isBound("run-1")).isTrue();
    }
}
