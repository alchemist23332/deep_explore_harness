package com.alchemist.deepexplore.harness.domain;

import java.util.Map;

public sealed interface RunEvent {

    String type();

    record RunStarted(
            String profileId,
            String userMessageId,
            String assistantMessageId,
            String searchProvider
    ) implements RunEvent {
        @Override
        public String type() {
            return "run_started";
        }
    }

    record TextDelta(String text) implements RunEvent {
        @Override
        public String type() {
            return "text_delta";
        }
    }

    record ToolCallStarted(
            String toolCallId,
            String toolName,
            String argumentsJson
    ) implements RunEvent {
        @Override
        public String type() {
            return "tool_call_started";
        }
    }

    record ToolCallCompleted(
            String toolCallId,
            String toolName,
            String result,
            boolean success
    ) implements RunEvent {
        @Override
        public String type() {
            return "tool_call_completed";
        }
    }

    record ApprovalRequired(
            String approvalId,
            String prompt
    ) implements RunEvent {
        @Override
        public String type() {
            return "approval_required";
        }
    }

    record ArtifactProduced(
            String artifactId,
            String kind,
            String uri,
            Map<String, Object> metadata
    ) implements RunEvent {
        @Override
        public String type() {
            return "artifact_produced";
        }
    }

    record RunCompleted(
            String assistantMessageId,
            String model,
            Integer tokenUsage
    ) implements RunEvent {
        @Override
        public String type() {
            return "run_completed";
        }
    }

    record RunFailed(
            String code,
            String message
    ) implements RunEvent {
        @Override
        public String type() {
            return "run_failed";
        }
    }

    record RunCancelled() implements RunEvent {
        @Override
        public String type() {
            return "run_cancelled";
        }
    }

    record CheckpointSaved(long version) implements RunEvent {
        @Override
        public String type() {
            return "checkpoint_saved";
        }
    }
}
