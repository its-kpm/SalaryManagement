package com.acme.salary.reporting.internal;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class CompensationReportRepository {
  private static final String BASE_FROM =
      """
      FROM employees e
      JOIN departments d ON d.id = e.department_id
      LEFT JOIN current_employee_compensation c ON c.employee_id = e.id
      LEFT JOIN exchange_rates r
        ON r.rate_set_id = :rateSetId AND r.source_currency = c.currency_code
      WHERE e.employment_status = 'ACTIVE'
      """;

  private final JdbcClient jdbc;

  CompensationReportRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  Optional<RateSet> resolveRateSet(UUID requestedId) {
    var sql =
        """
        SELECT rs.id, rs.reporting_currency, rs.as_of_date, rs.label, c.minor_units
        FROM exchange_rate_sets rs
        JOIN currencies c ON c.code = rs.reporting_currency
        """
            + (requestedId == null
                ? " ORDER BY rs.as_of_date DESC, rs.id ASC LIMIT 1"
                : " WHERE rs.id = :id");
    var statement = jdbc.sql(sql);
    if (requestedId != null) statement = statement.param("id", requestedId);
    return statement.query(CompensationReportRepository::rateSet).optional();
  }

  Metrics summary(UUID rateSetId, Filters filters) {
    var query = filtered(rateSetId, filters);
    var sql =
        """
        WITH base AS (
          SELECT e.id, c.salary_record_id, r.rate,
                 c.annual_base_amount * r.rate AS amount
        """
            + query.from()
            + """
        ), ranked AS (
          SELECT amount, row_number() OVER (ORDER BY amount, id) AS position,
                 count(*) OVER () AS value_count
          FROM base
          WHERE salary_record_id IS NOT NULL AND rate IS NOT NULL
        )
        SELECT (SELECT count(*) FROM base) AS expected_count,
               (SELECT count(*) FROM base
                 WHERE salary_record_id IS NOT NULL AND rate IS NOT NULL) AS complete_count,
               count(amount) AS employee_count,
               sum(amount) AS total_amount,
               avg(amount) AS average_amount,
               avg(amount) FILTER (
                 WHERE position IN ((value_count + 1) / 2, (value_count + 2) / 2)
               ) AS median_amount
        FROM ranked
        """;
    return jdbc.sql(sql)
        .params(query.parameters())
        .query(CompensationReportRepository::metrics)
        .single();
  }

  List<GroupMetrics> breakdown(UUID rateSetId, Filters filters, GroupBy groupBy) {
    var query = filtered(rateSetId, filters);
    var key = groupBy.keyExpression();
    var label = groupBy.labelExpression();
    var sql =
        """
        WITH base AS (
          SELECT e.id, %s AS group_key, %s AS group_label,
                 c.annual_base_amount * r.rate AS amount
        %s
          AND c.salary_record_id IS NOT NULL AND r.rate IS NOT NULL
        ), ranked AS (
          SELECT group_key, group_label, amount,
                 row_number() OVER (PARTITION BY group_key ORDER BY amount, id) AS position,
                 count(*) OVER (PARTITION BY group_key) AS value_count
          FROM base
        )
        SELECT group_key, group_label, count(*) AS employee_count,
               sum(amount) AS total_amount, avg(amount) AS average_amount,
               avg(amount) FILTER (
                 WHERE position IN ((value_count + 1) / 2, (value_count + 2) / 2)
               ) AS median_amount
        FROM ranked
        GROUP BY group_key, group_label
        ORDER BY group_label, group_key
        """
            .formatted(key, label, query.from());
    return jdbc.sql(sql)
        .params(query.parameters())
        .query(CompensationReportRepository::groupMetrics)
        .list();
  }

  private static RateSet rateSet(ResultSet row, int rowNumber) throws SQLException {
    return new RateSet(
        row.getObject("id", UUID.class),
        row.getString("reporting_currency"),
        row.getObject("as_of_date", LocalDate.class),
        row.getString("label"),
        row.getInt("minor_units"));
  }

  private static Metrics metrics(ResultSet row, int rowNumber) throws SQLException {
    return new Metrics(
        row.getLong("expected_count"),
        row.getLong("complete_count"),
        row.getLong("employee_count"),
        row.getBigDecimal("total_amount"),
        row.getBigDecimal("average_amount"),
        row.getBigDecimal("median_amount"));
  }

  private static GroupMetrics groupMetrics(ResultSet row, int rowNumber) throws SQLException {
    return new GroupMetrics(
        row.getString("group_key"),
        row.getString("group_label"),
        row.getLong("employee_count"),
        row.getBigDecimal("total_amount"),
        row.getBigDecimal("average_amount"),
        row.getBigDecimal("median_amount"));
  }

  private static FilteredQuery filtered(UUID rateSetId, Filters filters) {
    var from = new StringBuilder(BASE_FROM);
    var parameters = new LinkedHashMap<String, Object>();
    parameters.put("rateSetId", rateSetId);
    if (filters.departmentId() != null) {
      from.append(" AND e.department_id = :departmentId");
      parameters.put("departmentId", filters.departmentId());
    }
    if (filters.countryCode() != null) {
      from.append(" AND e.country_code = :countryCode");
      parameters.put("countryCode", filters.countryCode());
    }
    if (filters.jobLevel() != null) {
      from.append(" AND e.job_level = :jobLevel");
      parameters.put("jobLevel", filters.jobLevel());
    }
    return new FilteredQuery(from.toString(), parameters);
  }

  enum GroupBy {
    DEPARTMENT("e.department_id::text", "d.name"),
    COUNTRY("e.country_code", "e.country_code"),
    JOB_LEVEL("e.job_level", "e.job_level");

    private final String keyExpression;
    private final String labelExpression;

    GroupBy(String keyExpression, String labelExpression) {
      this.keyExpression = keyExpression;
      this.labelExpression = labelExpression;
    }

    String keyExpression() {
      return keyExpression;
    }

    String labelExpression() {
      return labelExpression;
    }
  }

  record Filters(UUID departmentId, String countryCode, String jobLevel) {}

  record RateSet(
      UUID id, String reportingCurrency, LocalDate asOfDate, String label, int minorUnits) {}

  record Metrics(
      long expectedCount,
      long completeCount,
      long employeeCount,
      BigDecimal total,
      BigDecimal average,
      BigDecimal median) {}

  record GroupMetrics(
      String key,
      String label,
      long employeeCount,
      BigDecimal total,
      BigDecimal average,
      BigDecimal median) {}

  private record FilteredQuery(String from, LinkedHashMap<String, Object> parameters) {}
}
