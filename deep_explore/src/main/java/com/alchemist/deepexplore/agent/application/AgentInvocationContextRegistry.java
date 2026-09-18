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
            String invocationId,
            String conversationId,
            String workspaceId
    ) {
        contexts.put(
                invocationId,
                new Context(conversationId, workspaceId)
        );
    }

    public Optional<Context> find(Object invocationId) {
        if (invocationId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(contexts.get(invocationId.toString()))
                .filter(Context::hasWorkspace);
    }

    public Context require(String invocationId) {
        return find(invocationId).orElseThrow(() ->
                new IllegalStateException(
                        "No workspace is bound to this Agent run"
                ));
    }

    public boolean isBound(Object invocationId) {
        return find(invocationId).isPresent();
    }

    public Optional<String> conversationId(Object invocationId) {
        if (invocationId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(contexts.get(invocationId.toString()))
                .map(Context::conversationId);
    }

    public void clear(String invocationId) {
        contexts.remove(invocationId);
    }

    public static final class Context {

        private final String conversationId;
        private final String workspaceId;
        private final ConcurrentMap<String, AtomicInteger> counters =
                new ConcurrentHashMap<>();

        private Context(String conversationId, String workspaceId) {
            this.conversationId = conversationId;
            this.workspaceId = workspaceId;
        }

        public String conversationId() {
            return conversationId;
        }

        public String workspaceId() {
            return workspaceId;
        }

        private boolean hasWorkspace() {
            return workspaceId != null && !workspaceId.isBlank();
        }

        public int increment(String counter) {
            return counters.computeIfAbsent(
                    counter,
                    ignored -> new AtomicInteger()
            ).incrementAndGet();
        }
    }
}
