package com.alchemist.deepexplore.harness.application.execution;

import com.alchemist.deepexplore.harness.application.command.StartRunCommand;
import com.alchemist.deepexplore.harness.domain.RunEventEnvelope;
import reactor.core.publisher.Flux;

public interface HarnessService {

    Flux<RunEventEnvelope> start(StartRunCommand command);
}
