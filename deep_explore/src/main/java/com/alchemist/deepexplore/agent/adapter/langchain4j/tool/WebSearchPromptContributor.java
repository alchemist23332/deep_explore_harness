package com.alchemist.deepexplore.agent.adapter.langchain4j.tool;

import com.alchemist.deepexplore.agent.adapter.langchain4j.prompt.PromptFragment;
import com.alchemist.deepexplore.agent.adapter.langchain4j.prompt.SystemPromptContributor;
import com.alchemist.deepexplore.agent.domain.AgentProfile;
import java.util.Collection;
import java.util.List;

public final class WebSearchPromptContributor
        implements SystemPromptContributor {

    private static final PromptFragment WEB_SEARCH_POLICY = new PromptFragment(
            "tool-web-search",
            1_000,
            "classpath:prompts/tools/web-search.md",
            "tool_policy"
    );

    @Override
    public Collection<PromptFragment> fragments(AgentProfile profile) {
        return List.of(WEB_SEARCH_POLICY);
    }
}
