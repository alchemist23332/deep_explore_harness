package com.alchemist.deepexplore.agent.application;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class AgentInvocationContextRegistry {

    private final ConcurrentMap<String, Context> contexts =
            new ConcurrentHashMap<>();

    public void bind(
            String conversationId,
            String runId,
            String workspaceId
    ) {
        if (workspaceId == null || workspaceId.isBlank()) {
            contexts.remove(conversationId);
            return;
        }
        contexts.put(conversationId, new Context(runId, workspaceId));
    }

    public Optional<Context> find(Object conversationId) {
        if (conversationId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(contexts.get(conversationId.toString()));
    }

    public Context require(String conversationId) {
        return find(conversationId).orElseThrow(() ->
                new IllegalStateException(
                        "No workspace is bound to this Agent run"
                ));
    }

    public boolean isBound(Object conversationId) {
        return find(conversationId).isPresent();
    }

    public void clear(String conversationId, String runId) {
        contexts.computeIfPresent(conversationId, (ignored, context) ->
                context.runId().equals(runId) ? null : context);
    }

    public void clear(String conversationId) {
        contexts.remove(conversationId);
    }

    public static final class Context {

        private final String runId;
        private final String workspaceId;
        private final ConcurrentMap<String, AtomicInteger> counters =
                new ConcurrentHashMap<>();

        private Context(String runId, String workspaceId) {
            this.runId = runId;
            this.workspaceId = workspaceId;
        }

        public String runId() {
            return runId;
        }

        public String workspaceId() {
            return workspaceId;
        }

        public int increment(String counter) {
            return counters.computeIfAbsent(
                    counter,
                    ignored -> new AtomicInteger()
            ).incrementAndGet();
        }
    }
}
