package com.alchemist.deepexplore.conversation.port;

import java.time.Duration;
import java.util.Optional;

public interface ConversationLock {

    Optional<Lease> tryAcquire(
            String conversationId,
            String ownerId,
            Duration timeout
    );

    boolean renew(Lease lease);

    boolean release(Lease lease);

    boolean releaseOwnedBy(String conversationId, String ownerId);

    record Lease(
            String conversationId,
            String ownerId,
            long version
    ) {
    }
}
