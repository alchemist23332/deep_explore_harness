package com.alchemist.deepexplore.coding.adapter.in.langchain4j;

import com.alchemist.deepexplore.agent.application.AgentInvocationContextRegistry;
import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.LangChainToolProvider;
import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.execution.ToolExecutionPolicy;
import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.execution.ToolResource;
import com.alchemist.deepexplore.agent.domain.ToolDescriptor;
import com.alchemist.deepexplore.agent.spi.ToolDescriptorContributor;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolSpecifications;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.DefaultToolExecutor;
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
public class CodingToolProvider
        implements LangChainToolProvider, ToolDescriptorContributor {

    private static final Map<String, ToolExecutionPolicy> TOOL_POLICIES = Map.ofEntries(
            Map.entry("list_files", ToolExecutionPolicy.readOnly(ToolResource.WORKSPACE)),
            Map.entry("read_file", ToolExecutionPolicy.readOnly(ToolResource.WORKSPACE)),
            Map.entry("grep_search", ToolExecutionPolicy.exclusive(ToolResource.WORKSPACE_RUNTIME)),
            Map.entry("write_file", ToolExecutionPolicy.mutation(ToolResource.WORKSPACE)),
            Map.entry("edit_file", ToolExecutionPolicy.mutation(ToolResource.WORKSPACE)),
            Map.entry("run_command", ToolExecutionPolicy.exclusive(ToolResource.WORKSPACE_RUNTIME)),
            Map.entry("start_preview", ToolExecutionPolicy.exclusive(ToolResource.WORKSPACE_RUNTIME)),
            Map.entry("preview_status", ToolExecutionPolicy.readOnly(ToolResource.WORKSPACE_RUNTIME)),
            Map.entry("preview_logs", ToolExecutionPolicy.readOnly(ToolResource.WORKSPACE_RUNTIME)),
            Map.entry("stop_preview", ToolExecutionPolicy.exclusive(ToolResource.WORKSPACE_RUNTIME))
    );

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

    @Override
    public Map<String, ToolExecutionPolicy> toolPolicies() {
        return TOOL_POLICIES;
    }

    @Override
    public List<ToolDescriptor> toolDescriptors() {
        return List.of(
                descriptor("list_files", "浏览文件"),
                descriptor("read_file", "读取文件"),
                descriptor("grep_search", "搜索代码"),
                descriptor("write_file", "写入文件"),
                descriptor("edit_file", "编辑文件"),
                // Keep historical events readable after removing the tool.
                descriptor("apply_patch", "应用补丁（已废弃）"),
                descriptor("run_command", "运行命令"),
                descriptor("start_preview", "启动预览"),
                descriptor("preview_status", "检查预览"),
                descriptor("preview_logs", "读取预览日志"),
                descriptor("stop_preview", "停止预览")
        );
    }

    private static ToolDescriptor descriptor(
            String name,
            String displayName
    ) {
        return new ToolDescriptor(
                name,
                displayName,
                ToolDescriptor.ArgumentExposure.DESCRIPTION,
                ToolDescriptor.ResultExposure.SUMMARY
        );
    }

    private static String toolName(Method method) {
        Tool tool = method.getAnnotation(Tool.class);
        return tool.name().isBlank() ? method.getName() : tool.name();
    }
}
