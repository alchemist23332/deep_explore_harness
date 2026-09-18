package com.alchemist.deepexplore.agent.adapter.langchain4j.tool;

import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.execution.ToolExecutionPolicy;
import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.execution.ToolResource;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "tools.web-search",
        name = "enabled",
        havingValue = "true"
)
public class WebSearchToolProvider implements LangChainToolProvider {

    private final List<AiServiceTool> tools;

    public WebSearchToolProvider(WebSearchToolAdapter adapter) {
        Method method;
        try {
            method = WebSearchToolAdapter.class.getMethod(
                    "search",
                    String.class,
                    String.class
            );
        } catch (NoSuchMethodException error) {
            throw new IllegalStateException(error);
        }
        this.tools = ToolSpecifications.toolSpecificationsFrom(adapter)
                .stream()
                .map(specification -> AiServiceTool.builder()
                        .toolSpecification(specification)
                        .toolExecutor(new DefaultToolExecutor(adapter, method))
                        .build())
                .toList();
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        return ToolProviderResult.builder().addAll(tools).build();
    }

    @Override
    public Map<String, ToolExecutionPolicy> toolPolicies() {
        return Map.of(
                "web_search",
                ToolExecutionPolicy.readOnly(ToolResource.EXTERNAL)
        );
    }
}
