package com.alchemist.deepexplore.agent.adapter.langchain4j.tool.execution;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ai.agent.tool-execution")
public record ToolExecutionProperties(
        @NotNull Mode mode,
        @Min(1) @Max(128) int maxParallelism,
        @Min(1) @Max(512) int maxCallsPerRound,
        @NotNull Duration barrierTimeout
) {

    public enum Mode {
        SEQUENTIAL,
        BARRIER
    }

    public boolean barrierEnabled() {
        return mode == Mode.BARRIER;
    }
}
