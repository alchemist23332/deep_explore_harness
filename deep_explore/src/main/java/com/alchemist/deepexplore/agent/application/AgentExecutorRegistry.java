package com.alchemist.deepexplore.agent.application;

import com.alchemist.deepexplore.agent.spi.AgentExecutor;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AgentExecutorRegistry {

    private final Map<String, AgentExecutor> executors;

    public AgentExecutorRegistry(List<AgentExecutor> executors) {
        this.executors = executors.stream()
                .collect(Collectors.toUnmodifiableMap(
                        AgentExecutor::agentId,
                        Function.identity()
                ));
    }

    public AgentExecutor require(String agentId) {
        AgentExecutor executor = executors.get(agentId);
        if (executor == null) {
            throw new UnknownAgentException(agentId);
        }
        return executor;
    }
}
