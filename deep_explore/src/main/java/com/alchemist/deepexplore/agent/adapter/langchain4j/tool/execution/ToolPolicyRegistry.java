package com.alchemist.deepexplore.agent.adapter.langchain4j.tool.execution;

import com.alchemist.deepexplore.agent.adapter.langchain4j.tool.LangChainToolProvider;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ToolPolicyRegistry {

    private static final Logger log =
            LoggerFactory.getLogger(ToolPolicyRegistry.class);
    private static final ToolExecutionPolicy UNKNOWN_POLICY =
            ToolExecutionPolicy.exclusive(ToolResource.WORKSPACE_RUNTIME);

    private final Map<String, ToolExecutionPolicy> policies;
    private final Set<String> warnedUnknownTools = ConcurrentHashMap.newKeySet();

    public ToolPolicyRegistry(List<LangChainToolProvider> providers) {
        Map<String, ToolExecutionPolicy> collected = new LinkedHashMap<>();
        for (LangChainToolProvider provider : providers) {
            provider.toolPolicies().forEach((name, policy) -> {
                ToolExecutionPolicy previous = collected.putIfAbsent(
                        name,
                        policy
                );
                if (previous != null) {
                    throw new IllegalStateException(
                            "Duplicate tool execution policy: " + name
                    );
                }
            });
        }
        this.policies = Map.copyOf(collected);
    }

    public ToolExecutionPolicy policyFor(String toolName) {
        ToolExecutionPolicy policy = policies.get(toolName);
        if (policy != null) {
            return policy;
        }
        if (warnedUnknownTools.add(toolName)) {
            log.warn(
                    "Tool '{}' has no execution policy; using exclusive mode",
                    toolName
            );
        }
        return UNKNOWN_POLICY;
    }

    public Optional<ToolExecutionPolicy> registeredPolicy(String toolName) {
        return Optional.ofNullable(policies.get(toolName));
    }

    public Map<String, ToolExecutionPolicy> policies() {
        return policies;
    }
}
