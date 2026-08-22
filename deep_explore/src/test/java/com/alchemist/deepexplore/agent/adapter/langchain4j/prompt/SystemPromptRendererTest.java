package com.alchemist.deepexplore.agent.adapter.langchain4j.prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.WebSearchPromptContributor;
import com.alchemist.deepexplore.agent.domain.AgentProfile;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class SystemPromptRendererTest {

    private final SystemPromptCatalog catalog = new SystemPromptCatalog(
            new DefaultResourceLoader()
    );

    @Test
    void rendersFastProfileWithoutDisabledToolPolicies() {
        SystemPromptRenderer renderer = new SystemPromptRenderer(
                catalog,
                List.of()
        );

        String prompt = renderer.render(AgentProfile.FAST);

        assertThat(prompt)
                .startsWith(
                        "<agent_system version=\"1\" profile=\"fast\">"
                )
                .contains("<assistant_core version=\"1\">")
                .contains("<execution_profile name=\"fast\">")
                .doesNotContain("<execution_profile name=\"deep\">")
                .doesNotContain("<tool_policy");
    }

    @Test
    void rendersDeepProfileAndEnabledToolPolicyInStableOrder() {
        SystemPromptRenderer renderer = new SystemPromptRenderer(
                catalog,
                List.of(new WebSearchPromptContributor())
        );

        String prompt = renderer.render(AgentProfile.DEEP);

        assertThat(prompt)
                .contains("<execution_profile name=\"deep\">")
                .contains("<tool_policy name=\"web_search\">");
        assertThat(prompt.indexOf("<assistant_core"))
                .isLessThan(prompt.indexOf("<execution_profile"));
        assertThat(prompt.indexOf("<execution_profile"))
                .isLessThan(prompt.indexOf("<tool_policy"));
        assertThat(occurrences(prompt, "<tool_policy name=\"web_search\">"))
                .isEqualTo(1);
    }

    @Test
    void rejectsDuplicateFragmentIds() {
        PromptFragment duplicate = new PromptFragment(
                "assistant-core",
                2_000,
                "classpath:prompts/tools/web-search.md",
                "tool_policy"
        );
        SystemPromptRenderer renderer = new SystemPromptRenderer(
                catalog,
                List.of(profile -> List.of(duplicate))
        );

        assertThatThrownBy(() -> renderer.render(AgentProfile.FAST))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Duplicate prompt fragment id: assistant-core");
    }

    @Test
    void rejectsMissingResourcesAndUnexpectedRootElements() {
        PromptFragment missing = new PromptFragment(
                "missing",
                1,
                "classpath:prompts/missing.md",
                "missing"
        );
        PromptFragment invalidRoot = new PromptFragment(
                "invalid-root",
                1,
                "classpath:prompts/invalid-root.md",
                "tool_policy"
        );

        assertThatThrownBy(() -> catalog.load(missing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not exist");
        assertThatThrownBy(() -> catalog.load(invalidRoot))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(
                        "Prompt root element must be <tool_policy>"
                );
    }

    private static int occurrences(String value, String target) {
        return (value.length() - value.replace(target, "").length())
                / target.length();
    }
}
