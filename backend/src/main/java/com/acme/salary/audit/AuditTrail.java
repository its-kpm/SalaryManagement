package com.acme.salary.audit;

import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/** Synchronous append-only audit contract. The caller's transaction owns atomicity. */
@Service
public class AuditTrail {
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private final JdbcClient jdbc;

  AuditTrail(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public UUID append(
      UUID actorId,
      String entityType,
      UUID entityId,
      String action,
      Map<String, ?> beforeValues,
      Map<String, ?> afterValues,
      String reason,
      UUID requestId) {
    var id = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO audit_events(
                id, actor_id, entity_type, entity_id, action,
                before_values, after_values, reason, request_id)
            VALUES (
                :id, :actorId, :entityType, :entityId, :action,
                CAST(:beforeValues AS jsonb), CAST(:afterValues AS jsonb), :reason, :requestId)
            """)
        .param("id", id)
        .param("actorId", actorId)
        .param("entityType", entityType)
        .param("entityId", entityId)
        .param("action", action)
        .param("beforeValues", json(beforeValues))
        .param("afterValues", json(afterValues))
        .param("reason", reason)
        .param("requestId", requestId)
        .update();
    return id;
  }

  private static String json(Map<String, ?> values) {
    return JSON.writeValueAsString(values);
  }
}
