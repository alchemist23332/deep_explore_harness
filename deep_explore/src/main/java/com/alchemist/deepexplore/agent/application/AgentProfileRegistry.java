package com.alchemist.deepexplore.agent.application;

import com.alchemist.deepexplore.agent.domain.AgentProfileDefinition;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AgentProfileRegistry {

    private final Map<String, AgentProfileDefinition> profiles;

    public AgentProfileRegistry(List<AgentProfileDefinition> profiles) {
        this.profiles = profiles.stream().collect(Collectors.toUnmodifiableMap(
                profile -> normalize(profile.id()),
                Function.identity()
        ));
    }

    public AgentProfileDefinition require(String profileId) {
        AgentProfileDefinition profile = profiles.get(normalize(profileId));
        if (profile == null) {
            throw new IllegalArgumentException(
                    "Unknown Agent profile: " + profileId
            );
        }
        return profile;
    }

    public List<AgentProfileDefinition> list() {
        return profiles.values().stream()
                .sorted(Comparator.comparing(AgentProfileDefinition::id))
                .toList();
    }

    private static String normalize(String profileId) {
        return profileId == null ? "" : profileId.strip().toLowerCase(
                Locale.ROOT
        );
    }
}
