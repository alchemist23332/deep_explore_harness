package com.alchemist.deepexplore.agent.adapter.langchain4j.prompt;

import com.alchemist.deepexplore.agent.domain.AgentProfile;
import java.util.Collection;

public interface SystemPromptContributor {

    Collection<PromptFragment> fragments(AgentProfile profile);

    default boolean supports(PromptContext context) {
        return true;
    }
}
