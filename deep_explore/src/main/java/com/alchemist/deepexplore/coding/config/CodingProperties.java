package com.alchemist.deepexplore.coding.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "tools.coding")
public record CodingProperties(
        boolean enabled,
        @Min(1) @Max(100) int maxToolCalls,
        @Min(1) @Max(50) int maxMutationCalls,
        @Min(1) @Max(20) int maxCommandCalls,
        @Min(1_000) int maxReadCharacters,
        @Min(1_000) int maxSearchCharacters,
        @Min(1) @Max(1_000) int maxSearchResults,
        @Min(1_000) int maxCommandResultCharacters
) {
}
