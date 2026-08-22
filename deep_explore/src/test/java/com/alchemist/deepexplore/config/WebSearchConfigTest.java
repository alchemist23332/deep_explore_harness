package com.alchemist.deepexplore.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.alchemist.deepexplore.agent.adapter.langchain4j.prompt.SystemPromptContributor;
import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.WebSearchToolAdapter;
import com.alchemist.deepexplore.agent.domain.WebSearchProvider;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class WebSearchConfigTest {

    @Test
    void keepsWebSearchDisabledWithoutAnApiKey() {
        contextRunner(properties(false, WebSearchProvider.JINA, ""))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(
                            WebSearchToolAdapter.class
                    );
                    assertThat(context).doesNotHaveBean(
                            SystemPromptContributor.class
                    );
                });
    }

    @Test
    void createsJinaToolWithoutAnApiKey() {
        contextRunner(properties(true, WebSearchProvider.JINA, ""))
                .withPropertyValues("tools.web-search.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(
                            WebSearchToolAdapter.class
                    );
                    assertThat(context).hasSingleBean(
                            SystemPromptContributor.class
                    );
                    WebSearchToolAdapter tool = context.getBean(
                            WebSearchToolAdapter.class
                    );
                    assertThat(tool.defaultProvider())
                            .isEqualTo(WebSearchProvider.JINA);
                    assertThat(tool.availableProviders())
                            .containsExactly(WebSearchProvider.JINA);
                });
    }

    @Test
    void keepsTavilyAvailableWhenItsApiKeyIsConfigured() {
        contextRunner(properties(
                true,
                WebSearchProvider.JINA,
                "test-key"
        ))
                .withPropertyValues("tools.web-search.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(WebSearchToolAdapter.class)
                            .availableProviders())
                            .containsExactly(
                                    WebSearchProvider.JINA,
                                    WebSearchProvider.TAVILY
                            );
                });
    }

    @Test
    void failsFastWhenTavilyIsDefaultWithoutAnApiKey() {
        contextRunner(properties(true, WebSearchProvider.TAVILY, ""))
                .withPropertyValues("tools.web-search.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseMessage(
                                    "TAVILY_API_KEY is required when "
                                            + "WEB_SEARCH_PROVIDER=TAVILY"
                            );
                });
    }

    private ApplicationContextRunner contextRunner(
            WebSearchProperties properties
    ) {
        return new ApplicationContextRunner()
                .withUserConfiguration(WebSearchConfig.class)
                .withBean(WebSearchProperties.class, () -> properties);
    }

    private static WebSearchProperties properties(
            boolean enabled,
            WebSearchProvider defaultProvider,
            String apiKey
    ) {
        return new WebSearchProperties(
                enabled,
                defaultProvider,
                400,
                16_384,
                new WebSearchProperties.Jina(
                        "",
                        "https://s.jina.ai/",
                        Duration.ofSeconds(20),
                        30_000
                ),
                new WebSearchProperties.Tavily(
                        apiKey,
                        "https://api.tavily.com/",
                        Duration.ofSeconds(10),
                        "basic",
                        false,
                        false
                )
        );
    }
}
