package com.alchemist.deepexplore.harness.adapter.out.postgres;

import com.alchemist.deepexplore.harness.domain.RunCheckpoint;
import com.alchemist.deepexplore.harness.port.CheckpointStore;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostgresCheckpointStore implements CheckpointStore {

    private final JdbcTemplate jdbcTemplate;

    public PostgresCheckpointStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public RunCheckpoint save(String runId, String stateJson) {
        Long version = jdbcTemplate.queryForObject("""
                SELECT COALESCE(MAX(version), 0) + 1
                FROM run_checkpoints
                WHERE run_id = ?
                """, Long.class, runId);
        jdbcTemplate.update("""
                INSERT INTO run_checkpoints (run_id, version, state_json)
                VALUES (?, ?, CAST(? AS jsonb))
                """, runId, version, stateJson);
        return findLatest(runId).orElseThrow();
    }

    @Override
    public Optional<RunCheckpoint> findLatest(String runId) {
        return jdbcTemplate.query("""
                        SELECT run_id, version, state_json::text, created_at
                        FROM run_checkpoints
                        WHERE run_id = ?
                        ORDER BY version DESC
                        LIMIT 1
                        """,
                (resultSet, rowNum) -> new RunCheckpoint(
                        resultSet.getString("run_id"),
                        resultSet.getLong("version"),
                        resultSet.getString("state_json"),
                        resultSet.getObject("created_at", OffsetDateTime.class).toInstant()
                ),
                runId
        ).stream().findFirst();
    }
}
