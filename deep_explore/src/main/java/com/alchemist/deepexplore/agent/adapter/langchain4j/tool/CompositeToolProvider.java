package com.alchemist.deepexplore.agent.adapter.langchain4j.tool;

import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.execution.ScheduledToolExecutor;
import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.execution.ToolBatchRegistry;
import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.execution.ToolPolicyRegistry;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class CompositeToolProvider implements ToolProvider {

    private final List<LangChainToolProvider> providers;
    private final ToolPolicyRegistry policies;
    private final ToolBatchRegistry batches;

    public CompositeToolProvider(
            List<LangChainToolProvider> providers,
            ToolPolicyRegistry policies,
            ToolBatchRegistry batches
    ) {
        this.providers = List.copyOf(providers);
        this.policies = policies;
        this.batches = batches;
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        ToolProviderResult.Builder result = ToolProviderResult.builder();
        Set<String> names = new HashSet<>();
        providers.forEach(provider -> provider.provideTools(request)
                .aiServiceTools()
                .forEach(tool -> {
                    String name = tool.name();
                    if (!names.add(name)) {
                        throw new IllegalStateException(
                                "Duplicate provided tool: " + name
                        );
                    }
                    policies.policyFor(name);
                    result.add(batches.barrierEnabled()
                            ? scheduled(tool)
                            : tool);
                }));
        return result.build();
    }

    private AiServiceTool scheduled(AiServiceTool tool) {
        return tool.toBuilder()
                .toolExecutor(new ScheduledToolExecutor(
                        tool.toolExecutor(),
                        batches
                ))
                .build();
    }
}
