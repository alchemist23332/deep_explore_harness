package com.alchemist.deepexplore.agent.adapter.langchain4j.tool;

import com.alchemist.deepexplore.agent.domain.WebSearchProvider;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.WebSearchTool;

public class TavilySearchClient implements WebSearchProviderClient {

    private final WebSearchTool delegate;

    public TavilySearchClient(WebSearchEngine searchEngine) {
        this.delegate = WebSearchTool.from(searchEngine);
    }

    @Override
    public WebSearchProvider provider() {
        return WebSearchProvider.TAVILY;
    }

    @Override
    public String search(String query) {
        return delegate.searchWeb(query);
    }
}
