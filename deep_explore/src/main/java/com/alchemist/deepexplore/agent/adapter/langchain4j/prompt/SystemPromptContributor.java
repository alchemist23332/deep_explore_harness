package com.alchemist.deepexplore.agent.adapter.langchain4j.prompt;

import java.util.Collection;

public interface SystemPromptContributor {

    Collection<PromptFragment> fragments(String profileId);

    default boolean supports(PromptContext context) {
        return true;
    }
}
