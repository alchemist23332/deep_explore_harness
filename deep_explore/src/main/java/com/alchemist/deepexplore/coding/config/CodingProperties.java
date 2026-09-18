package com.alchemist.deepexplore.coding.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "tools.coding")
public record CodingProperties(
        boolean enabled,
        @Min(32) @Max(10_000) int emergencyMaxToolCalls,
        @Min(1_000) int maxReadCharacters,
        @Min(1) @Max(256) int maxEditOperations,
        @Min(1_000) int maxEditInputCharacters,
        @Min(1_000) int maxSearchCharacters,
        @Min(1) @Max(1_000) int maxSearchResults,
        @Min(1_000) int maxCommandResultCharacters
) {
}
