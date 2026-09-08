package com.alchemist.deepexplore.agent.adapter.langchain4j.memory;

import static dev.langchain4j.data.message.ChatMessageDeserializer.messagesFromJson;
import static dev.langchain4j.data.message.ChatMessageSerializer.messagesToJson;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PostgresChatMemoryStore implements PersistentChatMemoryStore {

    private final JdbcTemplate jdbcTemplate;

    public PostgresChatMemoryStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean contains(Object memoryId) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM conversation_memory
                WHERE conversation_id = ?
                """, Integer.class, memoryId.toString());
        return count != null && count > 0;
    }

    @Override
    public boolean isDirty(Object memoryId) {
        return jdbcTemplate.query("""
                        SELECT dirty
                        FROM conversation_memory
                        WHERE conversation_id = ?
                        """,
                (resultSet, rowNumber) -> resultSet.getBoolean("dirty"),
                memoryId.toString()
        ).stream().findFirst().orElse(false);
    }

    @Override
    public void markDirty(Object memoryId) {
        jdbcTemplate.update("""
                UPDATE conversation_memory
                SET dirty = TRUE,
                    updated_at = CURRENT_TIMESTAMP
                WHERE conversation_id = ?
                """, memoryId.toString());
    }

    @Override
    public void clearDirty(Object memoryId) {
        jdbcTemplate.update("""
                UPDATE conversation_memory
                SET dirty = FALSE,
                    updated_at = CURRENT_TIMESTAMP
                WHERE conversation_id = ?
                """, memoryId.toString());
    }

    @Override
    public String sourceHeadMessageId(Object memoryId) {
        return jdbcTemplate.query("""
                        SELECT source_head_message_id
                        FROM conversation_memory
                        WHERE conversation_id = ?
                        """,
                resultSet -> resultSet.next()
                        ? resultSet.getString("source_head_message_id")
                        : null,
                memoryId.toString()
        );
    }

    @Override
    public void markSynchronized(
            Object memoryId,
            String sourceHeadMessageId
    ) {
        jdbcTemplate.update("""
                UPDATE conversation_memory
                SET source_head_message_id = ?,
                    dirty = FALSE,
                    updated_at = CURRENT_TIMESTAMP
                WHERE conversation_id = ?
                """, sourceHeadMessageId, memoryId.toString());
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        return jdbcTemplate.query("""
                        SELECT messages_json::text
                        FROM conversation_memory
                        WHERE conversation_id = ?
                        """,
                (resultSet, rowNum) -> messagesFromJson(resultSet.getString(1)),
                memoryId.toString()
        ).stream().findFirst().orElseGet(List::of);
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        List<ChatMessage> persistentMessages = messagesForPersistence(messages);
        jdbcTemplate.update("""
                INSERT INTO conversation_memory (
                    conversation_id, messages_json, version, updated_at
                )
                VALUES (?, CAST(? AS jsonb), 1, CURRENT_TIMESTAMP)
                ON CONFLICT (conversation_id) DO UPDATE
                SET messages_json = EXCLUDED.messages_json,
                    version = conversation_memory.version + 1,
                    updated_at = CURRENT_TIMESTAMP
                """, memoryId.toString(), messagesToJson(persistentMessages));
    }

    @Override
    public void deleteMessages(Object memoryId) {
        jdbcTemplate.update("""
                DELETE FROM conversation_memory
                WHERE conversation_id = ?
                """, memoryId.toString());
    }

    static List<ChatMessage> messagesForPersistence(
            List<ChatMessage> messages
    ) {
        return messages.stream()
                .map(message -> {
                    if (message instanceof AiMessage aiMessage
                            && !aiMessage.hasToolExecutionRequests()
                            && aiMessage.thinking() != null) {
                        return (ChatMessage) aiMessage.toBuilder()
                                .thinking(null)
                                .build();
                    }
                    return message;
                })
                .toList();
    }
}
