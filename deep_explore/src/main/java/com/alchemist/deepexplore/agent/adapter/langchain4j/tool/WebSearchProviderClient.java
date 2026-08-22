package com.alchemist.deepexplore.agent.adapter.langchain4j.tool;

import com.alchemist.deepexplore.agent.domain.WebSearchProvider;

public interface WebSearchProviderClient {

    WebSearchProvider provider();

    String search(String query);
}
