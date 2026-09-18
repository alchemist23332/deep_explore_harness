package com.alchemist.deepexplore.conversation.adapter.out.postgres;

import com.alchemist.deepexplore.conversation.port.ConversationLock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PostgresConversationLock implements ConversationLock {

    private final JdbcTemplate jdbcTemplate;

    public PostgresConversationLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<Lease> tryAcquire(
            String conversationId,
            String ownerId,
            Duration timeout
    ) {
        OffsetDateTime staleBefore = OffsetDateTime.ofInstant(
                Instant.now().minus(timeout),
                ZoneOffset.UTC
        );
        return jdbcTemplate.query("""
                UPDATE conversations
                SET generation_status = 'RUNNING',
                    generation_owner = ?,
                    generation_version = generation_version + 1,
                    generation_started_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND (
                    generation_status = 'IDLE'
                    OR generation_started_at < ?
                  )
                RETURNING generation_version
                """,
                (resultSet, rowNumber) -> new Lease(
                        conversationId,
                        ownerId,
                        resultSet.getLong("generation_version")
                ),
                ownerId,
                conversationId,
                staleBefore
        ).stream().findFirst();
    }

    @Override
    public boolean renew(Lease lease) {
        return jdbcTemplate.update("""
                UPDATE conversations
                SET generation_started_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND generation_status = 'RUNNING'
                  AND generation_owner = ?
                  AND generation_version = ?
                """,
                lease.conversationId(),
                lease.ownerId(),
                lease.version()
        ) == 1;
    }

    @Override
    public boolean release(Lease lease) {
        return jdbcTemplate.update("""
                UPDATE conversations
                SET generation_status = 'IDLE',
                    generation_owner = NULL,
                    generation_started_at = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND generation_status = 'RUNNING'
                  AND generation_owner = ?
                  AND generation_version = ?
                """,
                lease.conversationId(),
                lease.ownerId(),
                lease.version()
        ) == 1;
    }

    @Override
    public boolean releaseOwnedBy(String conversationId, String ownerId) {
        return jdbcTemplate.update("""
                UPDATE conversations
                SET generation_status = 'IDLE',
                    generation_owner = NULL,
                    generation_started_at = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND generation_status = 'RUNNING'
                  AND generation_owner = ?
                """, conversationId, ownerId) == 1;
    }
}
