package com.alchemist.deepexplore.harness.port;

import com.alchemist.deepexplore.harness.domain.RunCheckpoint;
import java.util.Optional;

public interface CheckpointStore {

    RunCheckpoint save(String runId, String stateJson);

    Optional<RunCheckpoint> findLatest(String runId);
}
