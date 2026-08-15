package com.alchemist.deepexplore.conversation.port;

import java.time.Duration;

public interface ConversationLock {

    boolean tryAcquire(String conversationId, Duration timeout);

    void release(String conversationId);
}
