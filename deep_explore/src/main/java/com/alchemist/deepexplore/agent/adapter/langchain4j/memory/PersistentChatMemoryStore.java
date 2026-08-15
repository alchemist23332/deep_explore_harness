package com.alchemist.deepexplore.agent.adapter.langchain4j.memory;

import dev.langchain4j.store.memory.chat.ChatMemoryStore;

public interface PersistentChatMemoryStore extends ChatMemoryStore {

    boolean contains(Object memoryId);
}
