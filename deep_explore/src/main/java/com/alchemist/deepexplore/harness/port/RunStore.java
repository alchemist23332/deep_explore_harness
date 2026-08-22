package com.alchemist.deepexplore.harness.port;

import com.alchemist.deepexplore.harness.domain.AgentRun;
import java.util.List;
import java.util.Optional;

public interface RunStore {

    AgentRun create(AgentRun run);

    Optional<AgentRun> find(String runId);

    List<AgentRun> listByConversation(String conversationId);

    void complete(String runId);

    void fail(String runId, String errorCode, String errorMessage);

    void cancel(String runId);
}
