package com.alchemist.deepexplore.agent.adapter.langchain4j.tool;

import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class CompositeToolProvider implements ToolProvider {

    private final List<LangChainToolProvider> providers;

    public CompositeToolProvider(List<LangChainToolProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        ToolProviderResult.Builder result = ToolProviderResult.builder();
        providers.forEach(provider ->
                result.addAll(provider.provideTools(request).aiServiceTools()));
        return result.build();
    }
}
