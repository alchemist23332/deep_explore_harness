package com.alchemist.deepexplore.harness.adapter.out.postgres;

import com.alchemist.deepexplore.harness.domain.AgentRun;
import com.alchemist.deepexplore.harness.domain.RunStatus;
import com.alchemist.deepexplore.harness.port.RunStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresRunStore implements RunStore {

    private final JdbcTemplate jdbcTemplate;

    public PostgresRunStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public AgentRun create(AgentRun run) {
        jdbcTemplate.update("""
                INSERT INTO agent_runs (
                    id, conversation_id, workspace_id, user_message_id,
                    assistant_message_id, agent_id, profile_id, status,
                    error_code, error_message, created_at, started_at,
                    completed_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                run.id(),
                run.conversationId(),
                run.workspaceId(),
                run.userMessageId(),
                run.assistantMessageId(),
                run.agentId(),
                run.profileId(),
                run.status().name(),
                run.errorCode(),
                run.errorMessage(),
                toOffsetDateTime(run.createdAt()),
                toOffsetDateTime(run.startedAt()),
                toOffsetDateTime(run.completedAt())
        );
        return run;
    }

    @Override
    public Optional<AgentRun> find(String runId) {
        return jdbcTemplate.query("""
                        SELECT id, conversation_id, user_message_id,
                               workspace_id, assistant_message_id, agent_id,
                               profile_id, status, error_code, error_message,
                               created_at, started_at, completed_at
                        FROM agent_runs
                        WHERE id = ?
                        """,
                (resultSet, rowNum) -> mapRun(resultSet),
                runId
        ).stream().findFirst();
    }

    @Override
    public List<AgentRun> listByConversation(String conversationId) {
        return jdbcTemplate.query("""
                        SELECT id, conversation_id, user_message_id,
                               workspace_id, assistant_message_id, agent_id,
                               profile_id, status, error_code, error_message,
                               created_at, started_at, completed_at
                        FROM agent_runs
                        WHERE conversation_id = ?
                        ORDER BY created_at
                        """,
                (resultSet, rowNum) -> mapRun(resultSet),
                conversationId
        );
    }

    @Override
    public void complete(String runId) {
        updateTerminalStatus(runId, RunStatus.COMPLETED, null, null);
    }

    @Override
    public void fail(String runId, String errorCode, String errorMessage) {
        updateTerminalStatus(runId, RunStatus.FAILED, errorCode, errorMessage);
    }

    @Override
    public void cancel(String runId) {
        updateTerminalStatus(runId, RunStatus.CANCELLED, null, null);
    }

    private void updateTerminalStatus(
            String runId,
            RunStatus status,
            String errorCode,
            String errorMessage
    ) {
        jdbcTemplate.update("""
                UPDATE agent_runs
                SET status = ?,
                    error_code = ?,
                    error_message = ?,
                    completed_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, status.name(), errorCode, errorMessage, runId);
    }

    private static OffsetDateTime toOffsetDateTime(Instant value) {
        return value == null ? null : value.atOffset(java.time.ZoneOffset.UTC);
    }

    private static AgentRun mapRun(ResultSet resultSet) throws SQLException {
        return new AgentRun(
                resultSet.getString("id"),
                resultSet.getString("conversation_id"),
                resultSet.getString("workspace_id"),
                resultSet.getString("user_message_id"),
                resultSet.getString("assistant_message_id"),
                resultSet.getString("agent_id"),
                resultSet.getString("profile_id"),
                RunStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("error_code"),
                resultSet.getString("error_message"),
                toInstant(resultSet.getObject("created_at", OffsetDateTime.class)),
                toInstant(resultSet.getObject("started_at", OffsetDateTime.class)),
                toInstant(resultSet.getObject("completed_at", OffsetDateTime.class))
        );
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
