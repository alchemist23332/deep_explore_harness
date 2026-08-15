package com.alchemist.deepexplore.harness.application;

import com.alchemist.deepexplore.harness.domain.RunEventEnvelope;
import reactor.core.publisher.Flux;

public interface HarnessService {

    Flux<RunEventEnvelope> start(StartRunCommand command);
}
