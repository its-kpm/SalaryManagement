package com.acme.salary.transfer.internal;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class EmployeeExportRepository {
  private final JdbcClient jdbc;

  EmployeeExportRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  List<ExportRow> find(Filters filters) {
    var where = new StringBuilder(" WHERE 1=1");
    var parameters = new LinkedHashMap<String, Object>();
    if (filters.search() != null) {
      where.append(
          " AND (lower(e.full_name) LIKE :search ESCAPE '\\'"
              + " OR lower(e.employee_number) LIKE :search ESCAPE '\\'"
              + " OR lower(e.email) LIKE :search ESCAPE '\\')");
      parameters.put("search", "%" + escapeLike(filters.search().toLowerCase(Locale.ROOT)) + "%");
    }
    if (filters.departmentId() != null) {
      where.append(" AND e.department_id = :departmentId");
      parameters.put("departmentId", filters.departmentId());
    }
    if (filters.countryCode() != null) {
      where.append(" AND e.country_code = :countryCode");
      parameters.put("countryCode", filters.countryCode());
    }
    if (filters.jobLevel() != null) {
      where.append(" AND e.job_level = :jobLevel");
      parameters.put("jobLevel", filters.jobLevel());
    }
    if (filters.status() != null) {
      where.append(" AND e.employment_status = :status");
      parameters.put("status", filters.status());
    }
    parameters.put("limit", 10_001);
    return jdbc.sql(
            """
            SELECT e.employee_number, e.full_name, e.email, d.name AS department,
                   e.country_code, e.job_title, e.job_level, e.employment_status,
                   c.annual_base_amount, c.currency_code, c.effective_from, s.reason
            FROM employees e
            JOIN departments d ON d.id = e.department_id
            LEFT JOIN current_employee_compensation c ON c.employee_id = e.id
            LEFT JOIN salary_records s ON s.id = c.salary_record_id
            """
                + where
                + " ORDER BY e.employee_number, e.id LIMIT :limit")
        .params(parameters)
        .query(
            (row, rowNumber) -> {
              var amount = row.getBigDecimal("annual_base_amount");
              return new ExportRow(
                  row.getString("employee_number"),
                  row.getString("full_name"),
                  row.getString("email"),
                  row.getString("department"),
                  row.getString("country_code"),
                  row.getString("job_title"),
                  row.getString("job_level"),
                  row.getString("employment_status"),
                  amount == null ? "" : amount.toPlainString(),
                  row.getString("currency_code"),
                  row.getObject("effective_from", LocalDate.class),
                  row.getString("reason"));
            })
        .list();
  }

  private static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  record Filters(
      String search, UUID departmentId, String countryCode, String jobLevel, String status) {}

  record ExportRow(
      String employeeNumber,
      String fullName,
      String email,
      String department,
      String countryCode,
      String jobTitle,
      String jobLevel,
      String employmentStatus,
      String annualBaseAmount,
      String currencyCode,
      LocalDate effectiveFrom,
      String reason) {}
}
