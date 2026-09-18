package com.alchemist.deepexplore.support;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.Callable;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Component
public class BlockingExecution {

    private final Scheduler jdbc = Schedulers.newBoundedElastic(
            32,
            10_000,
            "deep-explore-jdbc"
    );
    private final Scheduler docker = Schedulers.newBoundedElastic(
            16,
            2_000,
            "deep-explore-docker"
    );
    private final Scheduler file = Schedulers.newBoundedElastic(
            32,
            10_000,
            "deep-explore-file"
    );

    public <T> Mono<T> mono(Kind kind, Callable<T> action) {
        return Mono.fromCallable(action).subscribeOn(scheduler(kind));
    }

    @PreDestroy
    void close() {
        jdbc.dispose();
        docker.dispose();
        file.dispose();
    }

    private Scheduler scheduler(Kind kind) {
        return switch (kind) {
            case JDBC -> jdbc;
            case DOCKER -> docker;
            case FILE -> file;
        };
    }

    public enum Kind {
        JDBC,
        DOCKER,
        FILE
    }
}
