package com.alchemist.deepexplore.workspace.adapter.out.postgres;

import com.alchemist.deepexplore.config.SecurityProperties;
import com.alchemist.deepexplore.workspace.domain.RuntimeProfile;
import com.alchemist.deepexplore.workspace.domain.Workspace;
import com.alchemist.deepexplore.workspace.domain.WorkspaceStatus;
import com.alchemist.deepexplore.workspace.port.WorkspaceStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresWorkspaceStore implements WorkspaceStore {

    private final JdbcTemplate jdbcTemplate;
    private final SecurityProperties security;

    public PostgresWorkspaceStore(
            JdbcTemplate jdbcTemplate,
            SecurityProperties security
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.security = security;
    }

    @Override
    public Workspace create(
            String workspaceId,
            String ownerId,
            String name,
            RuntimeProfile runtimeProfile
    ) {
        jdbcTemplate.update("""
                INSERT INTO workspaces (
                    id,
                    tenant_id,
                    owner_id,
                    name,
                    runtime_profile
                )
                VALUES (?, ?, ?, ?, ?)
                """,
                workspaceId,
                security.tenantId(),
                ownerId,
                name,
                runtimeProfile.name()
        );
        return find(workspaceId, ownerId).orElseThrow();
    }

    @Override
    public Optional<Workspace> find(String workspaceId, String ownerId) {
        return jdbcTemplate.query("""
                        SELECT *
                        FROM workspaces
                        WHERE id = ?
                          AND tenant_id = ?
                          AND owner_id = ?
                        """,
                (resultSet, rowNum) -> map(resultSet),
                workspaceId,
                security.tenantId(),
                ownerId
        ).stream().findFirst();
    }

    @Override
    public List<Workspace> list(String ownerId) {
        return jdbcTemplate.query("""
                SELECT *
                FROM workspaces
                WHERE tenant_id = ?
                  AND owner_id = ?
                ORDER BY updated_at DESC
                """,
                (resultSet, rowNum) -> map(resultSet),
                security.tenantId(),
                ownerId
        );
    }

    @Override
    public void updateRuntime(
            String workspaceId,
            WorkspaceStatus status,
            String containerId,
            String lastError
    ) {
        jdbcTemplate.update("""
                UPDATE workspaces
                SET status = ?,
                    container_id = ?,
                    last_error = ?,
                    last_started_at = CASE
                        WHEN ? = 'RUNNING' THEN CURRENT_TIMESTAMP
                        ELSE last_started_at
                    END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                  AND tenant_id = ?
                  AND owner_id = ?
                """,
                status.name(),
                containerId,
                lastError,
                status.name(),
                workspaceId,
                security.tenantId(),
                security.ownerId()
        );
    }

    @Override
    public void delete(String workspaceId, String ownerId) {
        jdbcTemplate.update(
                """
                DELETE FROM workspaces
                WHERE id = ?
                  AND tenant_id = ?
                  AND owner_id = ?
                """,
                workspaceId,
                security.tenantId(),
                ownerId
        );
    }

    private static Workspace map(ResultSet resultSet) throws SQLException {
        return new Workspace(
                resultSet.getString("id"),
                resultSet.getString("owner_id"),
                resultSet.getString("name"),
                RuntimeProfile.valueOf(
                        resultSet.getString("runtime_profile")
                ),
                resultSet.getString("container_id"),
                WorkspaceStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("last_error"),
                resultSet.getObject(
                        "created_at",
                        OffsetDateTime.class
                ).toInstant(),
                resultSet.getObject(
                        "updated_at",
                        OffsetDateTime.class
                ).toInstant(),
                nullableInstant(resultSet, "last_started_at")
        );
    }

    private static java.time.Instant nullableInstant(
            ResultSet resultSet,
            String column
    ) throws SQLException {
        OffsetDateTime value = resultSet.getObject(
                column,
                OffsetDateTime.class
        );
        return value == null ? null : value.toInstant();
    }
}
