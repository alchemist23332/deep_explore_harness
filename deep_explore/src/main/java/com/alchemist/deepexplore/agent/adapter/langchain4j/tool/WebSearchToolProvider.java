package com.alchemist.deepexplore.agent.adapter.langchain4j.tool;

import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.lang.reflect.Method;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(WebSearchToolAdapter.class)
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
}
