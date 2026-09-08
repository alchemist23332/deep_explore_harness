package com.alchemist.deepexplore.agent.adapter.langchain4j.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alchemist.deepexplore.agent.domain.WebSearchProvider;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebSearchToolAdapterTest {

    @Test
    void usesDefaultProviderAndNormalizesQuery() {
        RecordingClient jina = new RecordingClient(WebSearchProvider.JINA);
        WebSearchRoutingContext routing = new WebSearchRoutingContext();
        WebSearchToolAdapter tool = new WebSearchToolAdapter(
                List.of(jina),
                WebSearchProvider.JINA,
                routing,
                400
        );

        String result = tool.search(
                "conversation-1",
                "  DeepSeek V4 release notes  "
        );

        assertThat(jina.query).isEqualTo("DeepSeek V4 release notes");
        assertThat(result)
                .startsWith("Search provider: JINA")
                .endsWith("JINA:DeepSeek V4 release notes");
    }

    @Test
    void usesTaskProviderOverride() {
        RecordingClient jina = new RecordingClient(WebSearchProvider.JINA);
        RecordingClient tavily = new RecordingClient(WebSearchProvider.TAVILY);
        WebSearchRoutingContext routing = new WebSearchRoutingContext();
        routing.bind("run-1", WebSearchProvider.TAVILY);
        WebSearchToolAdapter tool = new WebSearchToolAdapter(
                List.of(jina, tavily),
                WebSearchProvider.JINA,
                routing,
                400
        );

        String result = tool.search("run-1", "latest news");

        assertThat(tavily.query).isEqualTo("latest news");
        assertThat(jina.query).isNull();
        assertThat(result)
                .startsWith("Search provider: TAVILY")
                .endsWith("TAVILY:latest news");
    }

    @Test
    void rejectsBlankAndOversizedQueries() {
        WebSearchToolAdapter tool = new WebSearchToolAdapter(
                List.of(new RecordingClient(WebSearchProvider.JINA)),
                WebSearchProvider.JINA,
                new WebSearchRoutingContext(),
                10
        );

        assertThatThrownBy(() -> tool.search("conversation-1", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
        assertThatThrownBy(() -> tool.search(
                "conversation-1",
                "12345678901"
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds 10");
    }

    private static final class RecordingClient
            implements WebSearchProviderClient {

        private final WebSearchProvider provider;
        private String query;

        private RecordingClient(WebSearchProvider provider) {
            this.provider = provider;
        }

        @Override
        public WebSearchProvider provider() {
            return provider;
        }

        @Override
        public String search(String query) {
            this.query = query;
            return provider + ":" + query;
        }
    }
}
