package com.alchemist.deepexplore.agent.adapter.langchain4j.prompt;

import com.alchemist.deepexplore.agent.domain.AgentProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class SystemPromptRenderer {

    private static final PromptFragment CORE = new PromptFragment(
            "assistant-core",
            100,
            "classpath:prompts/assistant/base.md",
            "assistant_core"
    );
    private static final Map<AgentProfile, PromptFragment> PROFILES = Map.of(
            AgentProfile.FAST,
            new PromptFragment(
                    "profile-fast",
                    200,
                    "classpath:prompts/assistant/fast.md",
                    "execution_profile"
            ),
            AgentProfile.DEEP,
            new PromptFragment(
                    "profile-deep",
                    200,
                    "classpath:prompts/assistant/deep.md",
                    "execution_profile"
            )
    );

    private final SystemPromptCatalog catalog;
    private final List<SystemPromptContributor> contributors;
    private final Map<AgentProfile, String> cache = new ConcurrentHashMap<>();

    public SystemPromptRenderer(
            SystemPromptCatalog catalog,
            List<SystemPromptContributor> contributors
    ) {
        this.catalog = catalog;
        this.contributors = List.copyOf(contributors);
    }

    public String render(AgentProfile profile) {
        if (profile == null) {
            throw new IllegalArgumentException("Agent profile must not be null");
        }
        return cache.computeIfAbsent(profile, this::renderUncached);
    }

    private String renderUncached(AgentProfile profile) {
        List<PromptFragment> fragments = new ArrayList<>();
        fragments.add(CORE);
        fragments.add(PROFILES.get(profile));
        for (SystemPromptContributor contributor : contributors) {
            fragments.addAll(contributor.fragments(profile));
        }
        validateUniqueIds(fragments);
        fragments.sort(
                Comparator.comparingInt(PromptFragment::order)
                        .thenComparing(PromptFragment::id)
        );

        StringBuilder prompt = new StringBuilder()
                .append("<agent_system version=\"1\" profile=\"")
                .append(profile.id())
                .append("\">\n");
        for (int index = 0; index < fragments.size(); index++) {
            if (index > 0) {
                prompt.append("\n\n");
            }
            prompt.append(indent(catalog.load(fragments.get(index))));
        }
        prompt.append("\n</agent_system>");

        String rendered = prompt.toString();
        catalog.validateDocument(rendered, "agent_system");
        return rendered;
    }

    private static void validateUniqueIds(List<PromptFragment> fragments) {
        Set<String> ids = new HashSet<>();
        for (PromptFragment fragment : fragments) {
            if (!ids.add(fragment.id())) {
                throw new IllegalStateException(
                        "Duplicate prompt fragment id: " + fragment.id()
                );
            }
        }
    }

    private static String indent(String content) {
        return "  " + content.replace("\n", "\n  ");
    }
}
