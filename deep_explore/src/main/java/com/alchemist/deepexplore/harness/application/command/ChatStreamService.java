package com.alchemist.deepexplore.harness.application.command;

import com.alchemist.deepexplore.agent.domain.WebSearchProvider;
import com.alchemist.deepexplore.config.WebSearchProperties;
import com.alchemist.deepexplore.harness.application.execution.HarnessService;
import com.alchemist.deepexplore.harness.domain.RunEventEnvelope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

@Service
public class ChatStreamService {

    private final HarnessService harness;
    private final String defaultAgentId;
    private final WebSearchProvider defaultSearchProvider;

    public ChatStreamService(
            HarnessService harness,
            @Value("${ai.agent.id:assistant}") String defaultAgentId,
            WebSearchProperties webSearchProperties
    ) {
        this.harness = harness;
        this.defaultAgentId = defaultAgentId;
        this.defaultSearchProvider = webSearchProperties.defaultProvider();
    }

    public ChatStream stream(ChatCommand command) {
        WebSearchProvider searchProvider = command.searchProvider() == null
                ? defaultSearchProvider
                : command.searchProvider();
        StartRunCommand startRun = new StartRunCommand(
                command.conversationId(),
                command.message(),
                defaultAgentId,
                command.profile().id(),
                command.userMessageId(),
                command.userParentMessageId(),
                command.assistantMessageId(),
                searchProvider,
                command.workspaceId()
        );
        return new ChatStream(searchProvider, harness.start(startRun));
    }

    public record ChatStream(
            WebSearchProvider searchProvider,
            Flux<RunEventEnvelope> events
    ) {
    }
}
