package com.alchemist.deepexplore.harness.adapter.in.web;

import com.alchemist.deepexplore.harness.application.query.RunActivityQueryService;
import com.alchemist.deepexplore.harness.application.query.RunActivityQueryService.RunActivityView;
import com.alchemist.deepexplore.support.BlockingExecution;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/conversations/{conversationId}/run-activities")
public class RunActivityController {

    private final RunActivityQueryService runActivities;
    private final BlockingExecution blocking;

    public RunActivityController(
            RunActivityQueryService runActivities,
            BlockingExecution blocking
    ) {
        this.runActivities = runActivities;
        this.blocking = blocking;
    }

    @GetMapping
    public Mono<List<RunActivityView>> list(
            @PathVariable String conversationId
    ) {
        return blocking.mono(
                BlockingExecution.Kind.JDBC,
                () -> runActivities.list(conversationId)
        );
    }
}
