package com.alchemist.deepexplore.agent.adapter.langchain4j.tool;

import com.alchemist.deepexplore.agent.domain.WebSearchProvider;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class WebSearchRoutingContext {

    private final ConcurrentMap<String, WebSearchProvider> taskProviders =
            new ConcurrentHashMap<>();

    public void bind(String conversationId, WebSearchProvider provider) {
        taskProviders.put(conversationId, provider);
    }

    public WebSearchProvider providerFor(
            String conversationId,
            WebSearchProvider defaultProvider
    ) {
        return taskProviders.getOrDefault(conversationId, defaultProvider);
    }

    public void clear(String conversationId) {
        taskProviders.remove(conversationId);
    }
}
