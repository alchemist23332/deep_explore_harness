package com.alchemist.deepexplore.harness.port;

import com.alchemist.deepexplore.harness.domain.AgentRun;
import java.util.List;
import java.util.Optional;

public interface RunStore {

    AgentRun create(AgentRun run);

    Optional<AgentRun> find(String runId);

    List<AgentRun> listByConversation(String conversationId);

    List<AgentRun> listRunning();

    boolean complete(String runId, long expectedVersion);

    boolean fail(
            String runId,
            long expectedVersion,
            String errorCode,
            String errorMessage
    );

    boolean cancel(String runId, long expectedVersion);
}
