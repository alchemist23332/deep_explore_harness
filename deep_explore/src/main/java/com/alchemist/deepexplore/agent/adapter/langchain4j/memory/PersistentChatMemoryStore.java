package com.alchemist.deepexplore.agent.adapter.langchain4j.memory;

import dev.langchain4j.store.memory.chat.ChatMemoryStore;

public interface PersistentChatMemoryStore extends ChatMemoryStore {

    boolean contains(Object memoryId);

    boolean isDirty(Object memoryId);

    void markDirty(Object memoryId);

    void clearDirty(Object memoryId);

    String sourceHeadMessageId(Object memoryId);

    void markSynchronized(Object memoryId, String sourceHeadMessageId);
}
