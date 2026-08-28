package com.alchemist.deepexplore.workspace.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "sandbox")
public record WorkspaceProperties(
        boolean enabled,
        @NotBlank String ownerId,
        @NotBlank String dataDirectory,
        @NotNull Duration commandTimeout,
        @Min(1) int maxCommandOutputBytes,
        @Min(1) long maxFileBytes,
        @Min(1) long maxPreviewBytes,
        @Min(1) long maxZipBytes,
        @Min(1) long maxExtractedBytes,
        @Min(1) int maxExtractedFiles,
        @Valid @NotNull Docker docker
) {

    public record Docker(
            @NotBlank String host,
            @NotBlank String fullstackImage,
            @Min(1) long memoryBytes,
            @Min(1) long nanoCpus,
            @Min(1) long pidsLimit,
            @NotBlank String networkMode
    ) {
    }
}
