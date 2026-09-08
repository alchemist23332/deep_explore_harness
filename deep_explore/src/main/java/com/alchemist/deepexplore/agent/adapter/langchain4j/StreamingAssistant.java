package com.alchemist.deepexplore.agent.adapter.langchain4j;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.memory.ChatMemoryAccess;

public interface StreamingAssistant extends ChatMemoryAccess {

    TokenStream chat(
            @MemoryId String invocationId,
            @UserMessage String message
    );
}
