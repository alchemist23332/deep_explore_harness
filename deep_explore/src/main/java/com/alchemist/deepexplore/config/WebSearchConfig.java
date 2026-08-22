package com.alchemist.deepexplore.config;

import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.JinaSearchClient;
import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.TavilySearchClient;
import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.WebSearchProviderClient;
import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.WebSearchPromptContributor;
import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.WebSearchRoutingContext;
import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.WebSearchToolAdapter;
import com.alchemist.deepexplore.agent.adapter.langchain4j.prompt.SystemPromptContributor;
import com.alchemist.deepexplore.agent.domain.WebSearchProvider;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebSearchConfig {

    @Bean
    WebSearchRoutingContext webSearchRoutingContext() {
        return new WebSearchRoutingContext();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "tools.web-search",
            name = "enabled",
            havingValue = "true"
    )
    SystemPromptContributor webSearchPromptContributor() {
        return new WebSearchPromptContributor();
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "tools.web-search",
            name = "enabled",
            havingValue = "true"
    )
    WebSearchToolAdapter webSearchTool(
            WebSearchProperties properties,
            WebSearchRoutingContext routingContext
    ) {
        List<WebSearchProviderClient> clients = new ArrayList<>();
        clients.add(new JinaSearchClient(
                properties.jina().baseUrl(),
                properties.jina().apiKey(),
                properties.jina().timeout(),
                properties.jina().maxResultCharacters()
        ));
        if (properties.tavily().isConfigured()) {
            clients.add(new TavilySearchClient(
                    tavilyWebSearchEngine(properties.tavily())
            ));
        } else if (properties.defaultProvider() == WebSearchProvider.TAVILY) {
            throw new IllegalStateException(
                    "TAVILY_API_KEY is required when "
                            + "WEB_SEARCH_PROVIDER=TAVILY"
            );
        }
        return new WebSearchToolAdapter(
                clients,
                properties.defaultProvider(),
                routingContext,
                properties.maxQueryCharacters()
        );
    }

    private WebSearchEngine tavilyWebSearchEngine(
            WebSearchProperties.Tavily properties
    ) {
        return TavilyWebSearchEngine.builder()
                .apiKey(properties.apiKey())
                .baseUrl(properties.baseUrl())
                .timeout(properties.timeout())
                .searchDepth(properties.searchDepth())
                .includeAnswer(properties.includeAnswer())
                .includeRawContent(properties.includeRawContent())
                .build();
    }
}
