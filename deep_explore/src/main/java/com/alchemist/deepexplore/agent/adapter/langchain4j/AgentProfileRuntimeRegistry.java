package com.alchemist.deepexplore.agent.adapter.langchain4j;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AgentProfileRuntimeRegistry {

    private final Map<String, AgentProfileRuntime> runtimes;

    public AgentProfileRuntimeRegistry(List<AgentProfileRuntime> runtimes) {
        this.runtimes = runtimes.stream().collect(Collectors.toUnmodifiableMap(
                runtime -> normalize(runtime.profileId()),
                Function.identity()
        ));
    }

    public AgentProfileRuntime require(String profileId) {
        AgentProfileRuntime runtime = runtimes.get(normalize(profileId));
        if (runtime == null) {
            throw new IllegalArgumentException(
                    "Unknown Agent profile runtime: " + profileId
            );
        }
        return runtime;
    }

    public List<AgentProfileRuntime> list() {
        return List.copyOf(runtimes.values());
    }

    private static String normalize(String profileId) {
        return profileId == null ? "" : profileId.strip().toLowerCase(
                Locale.ROOT
        );
    }
}
