package com.acme.salary.compensation.internal;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CompensationRepository {
  private final JdbcClient jdbc;

  CompensationRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  Optional<SalaryPeriod> latestForUpdate(UUID employeeId) {
    return jdbc.sql(
            """
            SELECT id, annual_base_amount, currency_code, effective_from, effective_to,
                   reason, actor_id, created_at
            FROM salary_records
            WHERE employee_id = :employeeId
            ORDER BY effective_from DESC, id ASC
            LIMIT 1
            FOR UPDATE
            """)
        .param("employeeId", employeeId)
        .query(CompensationRepository::period)
        .optional();
  }

  void close(UUID salaryRecordId, LocalDate effectiveTo) {
    var updated =
        jdbc.sql(
                """
                UPDATE salary_records
                SET effective_to = :effectiveTo
                WHERE id = :id AND effective_to IS NULL
                """)
            .param("id", salaryRecordId)
            .param("effectiveTo", effectiveTo)
            .update();
    if (updated != 1) throw new IllegalStateException("Latest salary period is not open");
  }

  UUID insert(
      UUID employeeId,
      BigDecimal amount,
      String currencyCode,
      LocalDate effectiveFrom,
      String reason,
      UUID actorId) {
    var id = UUID.randomUUID();
    jdbc.sql(
            """
            INSERT INTO salary_records(
                id, employee_id, annual_base_amount, currency_code,
                effective_from, reason, actor_id)
            VALUES (:id, :employeeId, :amount, :currencyCode, :effectiveFrom, :reason, :actorId)
            """)
        .param("id", id)
        .param("employeeId", employeeId)
        .param("amount", amount)
        .param("currencyCode", currencyCode)
        .param("effectiveFrom", effectiveFrom)
        .param("reason", reason)
        .param("actorId", actorId)
        .update();
    return id;
  }

  SalaryPage history(UUID employeeId, int page, int size) {
    var total =
        jdbc.sql("SELECT count(*) FROM salary_records WHERE employee_id = :employeeId")
            .param("employeeId", employeeId)
            .query(Long.class)
            .single();
    var items =
        jdbc.sql(
                """
                SELECT id, annual_base_amount, currency_code, effective_from, effective_to,
                       reason, actor_id, created_at
                FROM salary_records
                WHERE employee_id = :employeeId
                ORDER BY effective_from DESC, id ASC
                LIMIT :limit OFFSET :offset
                """)
            .param("employeeId", employeeId)
            .param("limit", size)
            .param("offset", (long) page * size)
            .query(CompensationRepository::period)
            .list();
    return new SalaryPage(items, total);
  }

  private static SalaryPeriod period(ResultSet row, int rowNumber) throws SQLException {
    var amount = row.getBigDecimal("annual_base_amount");
    return new SalaryPeriod(
        row.getObject("id", UUID.class),
        amount.toPlainString(),
        row.getString("currency_code"),
        row.getObject("effective_from", LocalDate.class),
        row.getObject("effective_to", LocalDate.class),
        row.getString("reason"),
        row.getObject("actor_id", UUID.class),
        row.getObject("created_at", OffsetDateTime.class));
  }

  record SalaryPage(List<SalaryPeriod> items, long total) {}

  record SalaryPeriod(
      UUID id,
      String annualBaseAmount,
      String currencyCode,
      LocalDate effectiveFrom,
      LocalDate effectiveTo,
      String reason,
      UUID actorId,
      OffsetDateTime createdAt) {}
}
