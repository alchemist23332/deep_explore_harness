package com.alchemist.deepexplore.conversation.adapter.out.postgres;

import com.alchemist.deepexplore.conversation.domain.ConversationMessage;
import com.alchemist.deepexplore.conversation.port.MessageStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostgresMessageStore implements MessageStore {

    private final JdbcTemplate jdbcTemplate;

    public PostgresMessageStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public boolean append(
            String conversationId,
            String messageId,
            String parentMessageId,
            ConversationMessage.Role role,
            String content,
            ConversationMessage.Status status,
            String model,
            Integer tokenUsage
    ) {
        Long nextSequence = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(sequence_no), 0) + 1
                FROM messages
                WHERE conversation_id = ?
                """, Long.class, conversationId);
        int inserted = jdbcTemplate.update("""
                INSERT INTO messages (
                    id, conversation_id, parent_message_id, sequence_no,
                    role, content, status, model, token_usage
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO NOTHING
                """,
                messageId,
                conversationId,
                parentMessageId,
                nextSequence,
                role.name(),
                content,
                status.name(),
                model,
                tokenUsage
        );
        if (inserted == 0) {
            return false;
        }

        jdbcTemplate.update("""
                UPDATE conversations
                SET head_message_id = ?,
                    title = COALESCE(title, ?),
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                messageId,
                role == ConversationMessage.Role.USER ? titleFrom(content) : null,
                conversationId
        );
        return true;
    }

    @Override
    public List<ConversationMessage> list(String conversationId) {
        return jdbcTemplate.query("""
                        SELECT id, conversation_id, parent_message_id, sequence_no,
                               role, content, status, model, token_usage, created_at
                        FROM messages
                        WHERE conversation_id = ?
                        ORDER BY sequence_no
                        """,
                (resultSet, rowNum) -> mapMessage(resultSet),
                conversationId
        );
    }

    @Override
    public boolean exists(String conversationId, String messageId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM messages
                WHERE conversation_id = ?
                  AND id = ?
                """, Integer.class, conversationId, messageId);
        return count != null && count > 0;
    }

    @Override
    public List<ConversationMessage> branch(
            String conversationId,
            String headMessageId,
            int limit
    ) {
        if (headMessageId == null) {
            return List.of();
        }
        return jdbcTemplate.query("""
                        WITH RECURSIVE branch AS (
                            SELECT id, conversation_id, parent_message_id, sequence_no,
                                   role, content, status, model, token_usage, created_at
                            FROM messages
                            WHERE conversation_id = ?
                              AND id = ?
                            UNION ALL
                            SELECT parent.id, parent.conversation_id,
                                   parent.parent_message_id, parent.sequence_no,
                                   parent.role, parent.content, parent.status,
                                   parent.model, parent.token_usage, parent.created_at
                            FROM messages parent
                            JOIN branch child
                              ON parent.id = child.parent_message_id
                            WHERE parent.conversation_id = ?
                        )
                        SELECT *
                        FROM (
                            SELECT *
                            FROM branch
                            WHERE status = 'COMPLETE'
                            ORDER BY sequence_no DESC
                            LIMIT ?
                        ) recent
                        ORDER BY sequence_no
                        """,
                (resultSet, rowNum) -> mapMessage(resultSet),
                conversationId,
                headMessageId,
                conversationId,
                limit
        );
    }

    private static ConversationMessage mapMessage(ResultSet resultSet) throws SQLException {
        return new ConversationMessage(
                resultSet.getString("id"),
                resultSet.getString("conversation_id"),
                resultSet.getString("parent_message_id"),
                resultSet.getLong("sequence_no"),
                ConversationMessage.Role.valueOf(resultSet.getString("role")),
                resultSet.getString("content"),
                ConversationMessage.Status.valueOf(resultSet.getString("status")),
                resultSet.getString("model"),
                resultSet.getObject("token_usage", Integer.class),
                toInstant(resultSet.getObject("created_at", OffsetDateTime.class))
        );
    }

    private static String titleFrom(String content) {
        String normalized = content.replaceAll("\\s+", " ").strip();
        return normalized.substring(0, Math.min(40, normalized.length()));
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value.toInstant();
    }
}
