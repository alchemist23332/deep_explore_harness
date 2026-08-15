package com.alchemist.deepexplore.conversation.adapter.out.postgres;

import com.alchemist.deepexplore.conversation.domain.Conversation;
import com.alchemist.deepexplore.conversation.port.ConversationStore;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostgresConversationStore implements ConversationStore {

    private final JdbcTemplate jdbcTemplate;

    public PostgresConversationStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public Conversation create(String requestedId, String title) {
        String id = requestedId == null || requestedId.isBlank()
                ? UUID.randomUUID().toString()
                : requestedId;
        jdbcTemplate.update("""
                INSERT INTO conversations (id, title)
                VALUES (?, ?)
                ON CONFLICT (id) DO NOTHING
                """, id, normalizeTitle(title));
        return find(id).orElseThrow();
    }

    @Override
    public Optional<Conversation> find(String conversationId) {
        return jdbcTemplate.query("""
                        SELECT id, title, status, head_message_id, created_at, updated_at
                        FROM conversations
                        WHERE id = ?
                        """,
                (resultSet, rowNum) -> new Conversation(
                        resultSet.getString("id"),
                        resultSet.getString("title"),
                        Conversation.Status.valueOf(resultSet.getString("status")),
                        resultSet.getString("head_message_id"),
                        toInstant(resultSet.getObject("created_at", OffsetDateTime.class)),
                        toInstant(resultSet.getObject("updated_at", OffsetDateTime.class))
                ),
                conversationId
        ).stream().findFirst();
    }

    @Override
    public List<Conversation> list() {
        return jdbcTemplate.query("""
                SELECT id, title, status, head_message_id, created_at, updated_at
                FROM conversations
                ORDER BY updated_at DESC
                """, (resultSet, rowNum) -> new Conversation(
                resultSet.getString("id"),
                resultSet.getString("title"),
                Conversation.Status.valueOf(resultSet.getString("status")),
                resultSet.getString("head_message_id"),
                toInstant(resultSet.getObject("created_at", OffsetDateTime.class)),
                toInstant(resultSet.getObject("updated_at", OffsetDateTime.class))
        ));
    }

    @Override
    public void rename(String conversationId, String title) {
        jdbcTemplate.update("""
                UPDATE conversations
                SET title = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, normalizeTitle(title), conversationId);
    }

    @Override
    public void setStatus(String conversationId, Conversation.Status status) {
        jdbcTemplate.update("""
                UPDATE conversations
                SET status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, status.name(), conversationId);
    }

    @Override
    public void delete(String conversationId) {
        jdbcTemplate.update("DELETE FROM conversations WHERE id = ?", conversationId);
    }

    private static String normalizeTitle(String title) {
        if (title == null) {
            return null;
        }
        String normalized = title.strip();
        return normalized.isEmpty()
                ? null
                : normalized.substring(0, Math.min(80, normalized.length()));
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value.toInstant();
    }
}
