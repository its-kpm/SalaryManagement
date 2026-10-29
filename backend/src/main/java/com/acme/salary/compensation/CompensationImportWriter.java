package com.acme.salary.compensation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Compensation write contract used inside the transfer module's import transaction. */
@Service
public class CompensationImportWriter {
  private final JdbcClient jdbc;

  CompensationImportWriter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Result apply(
      UUID employeeId,
      BigDecimal amount,
      String currencyCode,
      LocalDate effectiveFrom,
      String reason,
      UUID actorId) {
    var previous =
        jdbc.sql(
                """
                SELECT id, annual_base_amount, currency_code, effective_from, effective_to
                FROM salary_records
                WHERE employee_id = :employeeId
                ORDER BY effective_from DESC, id ASC
                LIMIT 1 FOR UPDATE
                """)
            .param("employeeId", employeeId)
            .query(
                (row, rowNumber) ->
                    new Previous(
                        row.getObject("id", UUID.class),
                        row.getBigDecimal("annual_base_amount"),
                        row.getString("currency_code"),
                        row.getObject("effective_from", LocalDate.class),
                        row.getObject("effective_to", LocalDate.class)))
            .optional();
    if (previous.isPresent()
        && (previous.get().effectiveTo() != null
            || !effectiveFrom.isAfter(previous.get().effectiveFrom()))) {
      return new Result(Status.DATE_CONFLICT, null, Map.of(), Map.of());
    }
    if (previous.isPresent()) {
      jdbc.sql("UPDATE salary_records SET effective_to = :end WHERE id = :id")
          .param("end", effectiveFrom)
          .param("id", previous.get().id())
          .update();
    }
    var salaryId = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO salary_records(
                id, employee_id, annual_base_amount, currency_code,
                effective_from, reason, actor_id)
            VALUES (:id, :employeeId, :amount, :currency, :start, :reason, :actorId)
            """)
        .param("id", salaryId)
        .param("employeeId", employeeId)
        .param("amount", amount)
        .param("currency", currencyCode)
        .param("start", effectiveFrom)
        .param("reason", reason)
        .param("actorId", actorId)
        .update();
    var before = new LinkedHashMap<String, Object>();
    previous.ifPresent(
        salary -> {
          before.put("salaryRecordId", salary.id().toString());
          before.put("annualBaseAmount", salary.amount().toPlainString());
          before.put("currencyCode", salary.currencyCode());
          before.put("effectiveFrom", salary.effectiveFrom().toString());
        });
    var after = new LinkedHashMap<String, Object>();
    after.put("salaryRecordId", salaryId.toString());
    after.put("annualBaseAmount", amount.setScale(4).toPlainString());
    after.put("currencyCode", currencyCode);
    after.put("effectiveFrom", effectiveFrom.toString());
    return new Result(Status.APPLIED, salaryId, before, after);
  }

  public enum Status {
    APPLIED,
    DATE_CONFLICT
  }

  public record Result(
      Status status, UUID salaryRecordId, Map<String, Object> before, Map<String, Object> after) {}

  private record Previous(
      UUID id,
      BigDecimal amount,
      String currencyCode,
      LocalDate effectiveFrom,
      LocalDate effectiveTo) {}
}
