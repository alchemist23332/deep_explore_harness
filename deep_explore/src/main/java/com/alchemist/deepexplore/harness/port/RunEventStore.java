package com.alchemist.deepexplore.harness.port;

import com.alchemist.deepexplore.harness.domain.RunEventEnvelope;
import java.util.List;

public interface RunEventStore {

    RunEventEnvelope append(RunEventEnvelope event);

    List<RunEventEnvelope> list(String runId, long afterSequence);

    List<RunEventEnvelope> listByConversation(String conversationId);
}
