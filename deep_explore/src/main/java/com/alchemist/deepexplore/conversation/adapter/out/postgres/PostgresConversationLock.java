package com.alchemist.deepexplore.conversation.adapter.out.postgres;

import com.alchemist.deepexplore.conversation.port.ConversationLock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PostgresConversationLock implements ConversationLock {

    private final JdbcTemplate jdbcTemplate;

    public PostgresConversationLock(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean tryAcquire(String conversationId, Duration timeout) {
        OffsetDateTime staleBefore = OffsetDateTime.ofInstant(
                Instant.now().minus(timeout),
                ZoneOffset.UTC
        );
        return jdbcTemplate.update("""
                UPDATE conversations
                SET generation_status = 'RUNNING',
                    generation_started_at = CURRENT_TIMESTAMP,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND (
                    generation_status = 'IDLE'
                    OR generation_started_at < ?
                  )
                """, conversationId, staleBefore) == 1;
    }

    @Override
    public void release(String conversationId) {
        jdbcTemplate.update("""
                UPDATE conversations
                SET generation_status = 'IDLE',
                    generation_started_at = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, conversationId);
    }
}
