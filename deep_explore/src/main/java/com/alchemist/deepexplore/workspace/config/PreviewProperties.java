package com.alchemist.deepexplore.workspace.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "sandbox.preview")
public record PreviewProperties(
        boolean enabled,
        @Min(1_024) @Max(65_535) int containerPort,
        @NotBlank String host,
        @NotNull Duration startupTimeout,
        @NotNull Duration healthTimeout,
        @Min(1_024) int maxLogCharacters
) {
}
