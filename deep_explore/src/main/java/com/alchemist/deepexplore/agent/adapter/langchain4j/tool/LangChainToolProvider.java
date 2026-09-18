package com.alchemist.deepexplore.agent.adapter.langchain4j.tool;

import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.execution.ToolExecutionPolicy;
import dev.langchain4j.service.tool.ToolProvider;
import java.util.Map;

public interface LangChainToolProvider extends ToolProvider {

    Map<String, ToolExecutionPolicy> toolPolicies();
}
