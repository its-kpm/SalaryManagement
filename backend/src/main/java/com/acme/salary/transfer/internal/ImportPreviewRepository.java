package com.acme.salary.transfer.internal;

import java.time.OffsetDateTime;
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
}
