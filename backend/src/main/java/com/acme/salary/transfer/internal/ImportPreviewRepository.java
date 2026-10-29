package com.acme.salary.transfer.internal;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ImportPreviewRepository {
  private final JdbcClient jdbc;

  ImportPreviewRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  void insert(
      UUID id,
      UUID ownerId,
      String contentHash,
      String normalizedPreview,
      String expectedVersions,
      String status,
      String errors,
      OffsetDateTime expiresAt) {
    jdbc.sql(
            """
            INSERT INTO imports(
                id, owner_id, content_hash, normalized_preview, expected_versions,
                status, errors, expires_at)
            VALUES (
                :id, :ownerId, :contentHash, CAST(:normalizedPreview AS jsonb),
                CAST(:expectedVersions AS jsonb), :status, CAST(:errors AS jsonb), :expiresAt)
            """)
        .param("id", id)
        .param("ownerId", ownerId)
        .param("contentHash", contentHash)
        .param("normalizedPreview", normalizedPreview)
        .param("expectedVersions", expectedVersions)
        .param("status", status)
        .param("errors", errors)
        .param("expiresAt", expiresAt)
        .update();
  }

  Optional<LockedImport> lock(UUID id) {
    return jdbc.sql(
            """
            SELECT id, owner_id, status, expires_at,
                   normalized_preview::text AS normalized_preview,
                   receipt::text AS receipt
            FROM imports
            WHERE id = :id
            FOR UPDATE
            """)
        .param("id", id)
        .query(
            (row, rowNumber) ->
                new LockedImport(
                    row.getObject("id", UUID.class),
                    row.getObject("owner_id", UUID.class),
                    row.getString("status"),
                    row.getObject("expires_at", OffsetDateTime.class),
                    row.getString("normalized_preview"),
                    row.getString("receipt")))
        .optional();
  }

  void markApplied(UUID id, OffsetDateTime appliedAt, String receipt) {
    jdbc.sql(
            """
            UPDATE imports
            SET status = 'APPLIED', applied_at = :appliedAt,
                receipt = CAST(:receipt AS jsonb),
                normalized_preview = normalized_preview - 'rows'
            WHERE id = :id AND status = 'VALIDATED'
            """)
        .param("id", id)
        .param("appliedAt", appliedAt)
        .param("receipt", receipt)
        .update();
  }

  record LockedImport(
      UUID id,
      UUID ownerId,
      String status,
      OffsetDateTime expiresAt,
      String normalizedPreview,
      String receipt) {}
}
