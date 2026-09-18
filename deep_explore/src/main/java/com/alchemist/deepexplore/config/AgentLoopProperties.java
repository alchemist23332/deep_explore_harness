package com.alchemist.deepexplore.config;

import com.alchemist.deepexplore.agent.domain.AgentProfile;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "ai.agent")
public record AgentLoopProperties(
        @Min(1) @Max(200) int fastMaxToolCallingRoundTrips,
        @Min(1) @Max(200) int deepMaxToolCallingRoundTrips
) {

    public int maxToolCallingRoundTrips(String profileId) {
        if (AgentProfile.FAST.id().equals(profileId)) {
            return fastMaxToolCallingRoundTrips;
        }
        if (AgentProfile.DEEP.id().equals(profileId)) {
            return deepMaxToolCallingRoundTrips;
        }
        throw new IllegalArgumentException("Unknown Agent profile: " + profileId);
    }
}
