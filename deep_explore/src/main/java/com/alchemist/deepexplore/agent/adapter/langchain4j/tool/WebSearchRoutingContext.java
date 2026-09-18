package com.alchemist.deepexplore.agent.adapter.langchain4j.tool;

import com.alchemist.deepexplore.agent.domain.WebSearchProvider;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class WebSearchRoutingContext {

    private final ConcurrentMap<String, WebSearchProvider> taskProviders =
            new ConcurrentHashMap<>();

    public void bind(
            String invocationId,
            WebSearchProvider provider
    ) {
        taskProviders.put(invocationId, provider);
    }

    public WebSearchProvider providerFor(
            String invocationId,
            WebSearchProvider defaultProvider
    ) {
        return taskProviders.getOrDefault(invocationId, defaultProvider);
    }

    public void clear(String invocationId) {
        taskProviders.remove(invocationId);
    }
}
