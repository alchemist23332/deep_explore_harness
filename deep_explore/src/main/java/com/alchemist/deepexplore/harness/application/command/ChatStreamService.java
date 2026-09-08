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
        StartRunCommand startRun = toStartRun(command);
        return new ChatStream(
                startRun.searchProvider(),
                harness.start(startRun)
        );
    }

    public StartRunCommand toStartRun(ChatCommand command) {
        WebSearchProvider searchProvider = command.searchProvider() == null
                ? defaultSearchProvider
                : command.searchProvider();
        String agentId = command.agentId() == null
                || command.agentId().isBlank()
                ? defaultAgentId
                : command.agentId();
        return new StartRunCommand(
                command.conversationId(),
                command.message(),
                agentId,
                command.resolvedProfileId(),
                command.userMessageId(),
                command.userParentMessageId(),
                command.assistantMessageId(),
                searchProvider,
                command.workspaceId()
        );
    }

    public record ChatStream(
            WebSearchProvider searchProvider,
            Flux<RunEventEnvelope> events
    ) {
    }
}
