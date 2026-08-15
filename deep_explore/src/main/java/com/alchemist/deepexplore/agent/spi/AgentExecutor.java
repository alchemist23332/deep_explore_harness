package com.alchemist.deepexplore.agent.spi;

import com.alchemist.deepexplore.agent.domain.AgentExecutionEvent;
import com.alchemist.deepexplore.agent.domain.AgentExecutionRequest;
import com.alchemist.deepexplore.agent.domain.AgentStateSnapshot;
import reactor.core.publisher.Flux;

public interface AgentExecutor {

    String agentId();

    boolean isConfigured();

    AgentStateSnapshot prepare(
            String conversationId,
            boolean replay,
            String rewindHeadMessageId
    );

    Flux<AgentExecutionEvent> execute(AgentExecutionRequest request);

    void restore(String conversationId, AgentStateSnapshot snapshot);

    void release(String conversationId);
}
