package com.alchemist.deepexplore.harness.adapter.in.web;

import com.alchemist.deepexplore.harness.application.query.RunActivityQueryService;
import com.alchemist.deepexplore.harness.application.query.RunActivityQueryService.RunActivityView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations/{conversationId}/run-activities")
public class RunActivityController {

    private final RunActivityQueryService runActivities;

    public RunActivityController(RunActivityQueryService runActivities) {
        this.runActivities = runActivities;
    }

    @GetMapping
    public List<RunActivityView> list(@PathVariable String conversationId) {
        return runActivities.list(conversationId);
    }
}
