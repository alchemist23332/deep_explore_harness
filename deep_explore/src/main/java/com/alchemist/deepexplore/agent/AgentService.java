package com.alchemist.deepexplore.agent;

import reactor.core.publisher.Flux;

public interface AgentService {

    Flux<AgentEvent> stream(AgentCommand command);
}
