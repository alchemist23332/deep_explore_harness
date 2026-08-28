package com.alchemist.deepexplore.coding.adapter.in.langchain4j;

import com.alchemist.deepexplore.agent.adapter.langchain4j.prompt.PromptContext;
import com.alchemist.deepexplore.agent.adapter.langchain4j.prompt.PromptFragment;
import com.alchemist.deepexplore.agent.adapter.langchain4j.prompt.SystemPromptContributor;
import com.alchemist.deepexplore.agent.domain.AgentProfile;
import java.util.Collection;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "tools.coding",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class CodingPromptContributor implements SystemPromptContributor {

    private static final PromptFragment CODING_POLICY = new PromptFragment(
            "tool-coding-workspace",
            1_100,
            "classpath:prompts/tools/coding-workspace.md",
            "coding_workspace"
    );

    @Override
    public Collection<PromptFragment> fragments(AgentProfile profile) {
        return List.of(CODING_POLICY);
    }

    @Override
    public boolean supports(PromptContext context) {
        return context.codingWorkspace();
    }
}
