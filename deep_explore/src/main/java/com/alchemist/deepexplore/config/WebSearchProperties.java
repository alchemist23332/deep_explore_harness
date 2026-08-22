package com.alchemist.deepexplore.config;

import com.alchemist.deepexplore.agent.domain.WebSearchProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "tools.web-search")
public record WebSearchProperties(
        boolean enabled,
        @NotNull WebSearchProvider defaultProvider,
        @Min(1) int maxQueryCharacters,
        @Min(1) int maxEventResultCharacters,
        @Valid @NotNull Jina jina,
        @Valid @NotNull Tavily tavily
) {

    public record Jina(
            String apiKey,
            @NotBlank String baseUrl,
            @NotNull Duration timeout,
            @Min(1) int maxResultCharacters
    ) {
    }

    public record Tavily(
            String apiKey,
            @NotBlank String baseUrl,
            @NotNull Duration timeout,
            @NotBlank String searchDepth,
            boolean includeAnswer,
            boolean includeRawContent
    ) {

        public boolean isConfigured() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
