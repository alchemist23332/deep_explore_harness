package com.alchemist.deepexplore.coding.adapter.in.langchain4j;

import com.alchemist.deepexplore.agent.application.AgentInvocationContextRegistry;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.DefaultToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "tools.coding",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class CodingToolProvider implements ToolProvider {

    private final AgentInvocationContextRegistry contexts;
    private final List<AiServiceTool> tools;

    public CodingToolProvider(
            AgentInvocationContextRegistry contexts,
            CodingTools codingTools
    ) {
        this.contexts = contexts;
        Map<String, Method> methods = Arrays.stream(
                        CodingTools.class.getDeclaredMethods()
                )
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .collect(Collectors.toUnmodifiableMap(
                        CodingToolProvider::toolName,
                        Function.identity()
                ));
        this.tools = ToolSpecifications.toolSpecificationsFrom(codingTools)
                .stream()
                .map(specification -> AiServiceTool.builder()
                        .toolSpecification(specification)
                        .toolExecutor(new DefaultToolExecutor(
                                codingTools,
                                methods.get(specification.name())
                        ))
                        .build())
                .toList();
    }

    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        if (!contexts.isBound(request.chatMemoryId())) {
            return ToolProviderResult.builder().build();
        }
        return ToolProviderResult.builder()
                .addAll(tools)
                .build();
    }

    private static String toolName(Method method) {
        Tool tool = method.getAnnotation(Tool.class);
        return tool.name().isBlank() ? method.getName() : tool.name();
    }
}
