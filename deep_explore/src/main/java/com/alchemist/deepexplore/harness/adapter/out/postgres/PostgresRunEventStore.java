package com.alchemist.deepexplore.harness.adapter.out.postgres;

import com.alchemist.deepexplore.harness.domain.RunEvent;
import com.alchemist.deepexplore.harness.domain.RunEventEnvelope;
import com.alchemist.deepexplore.harness.port.RunEventStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostgresRunEventStore implements RunEventStore {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public PostgresRunEventStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public RunEventEnvelope append(RunEventEnvelope envelope) {
        Long sequence = jdbcTemplate.queryForObject("""
                UPDATE agent_runs
                SET last_event_sequence = last_event_sequence + 1
                WHERE id = ?
                RETURNING last_event_sequence
                """, Long.class, envelope.runId());
        if (sequence == null) {
            throw new IllegalStateException(
                    "Unable to allocate event sequence for run "
                            + envelope.runId()
            );
        }
        RunEventEnvelope persisted = new RunEventEnvelope(
                envelope.eventId(),
                envelope.runId(),
                envelope.conversationId(),
                envelope.agentId(),
                sequence,
                envelope.occurredAt(),
                envelope.event()
        );
        jdbcTemplate.update("""
                INSERT INTO agent_run_events (
                    event_id, run_id, sequence_no, event_type,
                    payload_json, occurred_at
                )
                VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?)
                """,
                persisted.eventId(),
                persisted.runId(),
                persisted.sequence(),
                persisted.event().type(),
                toJson(persisted.event()),
                persisted.occurredAt().atOffset(java.time.ZoneOffset.UTC)
        );
        return persisted;
    }

    @Override
    public List<RunEventEnvelope> list(String runId, long afterSequence) {
        return jdbcTemplate.query("""
                        SELECT event_id, run_id, sequence_no, event_type,
                               payload_json::text, occurred_at,
                               r.conversation_id, r.agent_id
                        FROM agent_run_events e
                        JOIN agent_runs r ON r.id = e.run_id
                        WHERE run_id = ?
                          AND sequence_no > ?
                        ORDER BY sequence_no
                        """,
                (resultSet, rowNum) -> mapEvent(resultSet),
                runId,
                afterSequence
        );
    }

    @Override
    public List<RunEventEnvelope> listByConversation(String conversationId) {
        return jdbcTemplate.query("""
                        SELECT event_id, run_id, sequence_no, event_type,
                               payload_json::text, occurred_at,
                               r.conversation_id, r.agent_id
                        FROM agent_run_events e
                        JOIN agent_runs r ON r.id = e.run_id
                        WHERE r.conversation_id = ?
                        ORDER BY r.created_at, e.sequence_no
                        """,
                (resultSet, rowNum) -> mapEvent(resultSet),
                conversationId
        );
    }

    private RunEventEnvelope mapEvent(ResultSet resultSet) throws SQLException {
        String type = resultSet.getString("event_type");
        String payload = resultSet.getString("payload_json");
        return new RunEventEnvelope(
                resultSet.getString("event_id"),
                resultSet.getString("run_id"),
                resultSet.getString("conversation_id"),
                resultSet.getString("agent_id"),
                resultSet.getLong("sequence_no"),
                resultSet.getObject("occurred_at", OffsetDateTime.class).toInstant(),
                fromJson(type, payload)
        );
    }

    private String toJson(RunEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Unable to serialize run event", error);
        }
    }

    private RunEvent fromJson(String type, String payload) {
        Class<? extends RunEvent> eventType = switch (type) {
            case "run_started" -> RunEvent.RunStarted.class;
            case "text_delta" -> RunEvent.TextDelta.class;
            case "tool_call_started" -> RunEvent.ToolCallStarted.class;
            case "tool_call_completed" -> RunEvent.ToolCallCompleted.class;
            case "approval_required" -> RunEvent.ApprovalRequired.class;
            case "artifact_produced" -> RunEvent.ArtifactProduced.class;
            case "run_completed" -> RunEvent.RunCompleted.class;
            case "run_failed" -> RunEvent.RunFailed.class;
            case "run_cancelled" -> RunEvent.RunCancelled.class;
            case "checkpoint_saved" -> RunEvent.CheckpointSaved.class;
            default -> throw new IllegalStateException("Unknown run event type: " + type);
        };
        try {
            return objectMapper.readValue(payload, eventType);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Unable to deserialize run event: " + type, error);
        }
    }
}
