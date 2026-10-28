package com.acme.salary.employee.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class EmployeeDirectoryRepository {
  private static final Map<String, String> SORT_COLUMNS =
      Map.of(
          "fullName", "e.full_name",
          "employeeNumber", "e.employee_number",
          "department", "d.name",
          "country", "e.country_code",
          "jobLevel", "e.job_level",
          "currentSalary", "c.annual_base_amount");

  private static final String FROM =
      """
      FROM employees e
      JOIN departments d ON d.id = e.department_id
      LEFT JOIN current_employee_compensation c ON c.employee_id = e.id
      """;

  private final JdbcClient jdbc;

  EmployeeDirectoryRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  EmployeePage find(EmployeeQuery query) {
    var where = new StringBuilder(" WHERE 1=1");
    var parameters = new java.util.LinkedHashMap<String, Object>();
    if (query.search() != null) {
      where.append(
          " AND (lower(e.full_name) LIKE :search ESCAPE '\\'"
              + " OR lower(e.employee_number) LIKE :search ESCAPE '\\'"
              + " OR lower(e.email) LIKE :search ESCAPE '\\')");
      parameters.put(
          "search", "%" + escapeLike(query.search().toLowerCase(java.util.Locale.ROOT)) + "%");
    }
    if (query.departmentId() != null) {
      where.append(" AND e.department_id = :departmentId");
      parameters.put("departmentId", query.departmentId());
    }
    if (query.countryCode() != null) {
      where.append(" AND e.country_code = :countryCode");
      parameters.put("countryCode", query.countryCode());
    }
    if (query.jobLevel() != null) {
      where.append(" AND e.job_level = :jobLevel");
      parameters.put("jobLevel", query.jobLevel());
    }
    if (query.status() != null) {
      where.append(" AND e.employment_status = :status");
      parameters.put("status", query.status());
    }

    var total =
        jdbc.sql("SELECT count(*) " + FROM + where).params(parameters).query(Long.class).single();
    parameters.put("limit", query.size());
    parameters.put("offset", (long) query.page() * query.size());
    var sortColumn = SORT_COLUMNS.get(query.sort());
    var sql =
        """
        SELECT e.id, e.employee_number, e.full_name, e.email, d.id AS department_id,
               d.name AS department_name, e.country_code, e.job_title, e.job_level,
               e.employment_status, e.version, c.annual_base_amount, c.currency_code
        """
            + FROM
            + where
            + " ORDER BY "
            + sortColumn
            + " "
            + query.direction()
            + " NULLS LAST, e.id ASC LIMIT :limit OFFSET :offset";
    var items = jdbc.sql(sql).params(parameters).query(EmployeeDirectoryRepository::summary).list();
    return new EmployeePage(items, total);
  }

  Optional<EmployeeDetails> findById(UUID id) {
    return jdbc.sql(
            """
            SELECT e.id, e.employee_number, e.full_name, e.email, d.id AS department_id,
                   d.name AS department_name, e.country_code, e.job_title, e.job_level,
                   e.employment_status, e.version, e.created_at, e.updated_at,
                   c.salary_record_id, c.annual_base_amount, c.currency_code,
                   c.effective_from AS salary_effective_from
            FROM employees e
            JOIN departments d ON d.id = e.department_id
            LEFT JOIN current_employee_compensation c ON c.employee_id = e.id
            WHERE e.id = :id
            """)
        .param("id", id)
        .query(EmployeeDirectoryRepository::details)
        .optional();
  }

  boolean supportsSort(String sort) {
    return SORT_COLUMNS.containsKey(sort);
  }

  private static String escapeLike(String value) {
    return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static EmployeeSummary summary(ResultSet row, int rowNumber) throws SQLException {
    return new EmployeeSummary(
        row.getObject("id", UUID.class),
        row.getString("employee_number"),
        row.getString("full_name"),
        row.getString("email"),
        row.getObject("department_id", UUID.class),
        row.getString("department_name"),
        row.getString("country_code"),
        row.getString("job_title"),
        row.getString("job_level"),
        row.getString("employment_status"),
        row.getLong("version"),
        decimal(row, "annual_base_amount"),
        row.getString("currency_code"));
  }

  private static EmployeeDetails details(ResultSet row, int rowNumber) throws SQLException {
    return new EmployeeDetails(
        row.getObject("id", UUID.class),
        row.getString("employee_number"),
        row.getString("full_name"),
        row.getString("email"),
        row.getObject("department_id", UUID.class),
        row.getString("department_name"),
        row.getString("country_code"),
        row.getString("job_title"),
        row.getString("job_level"),
        row.getString("employment_status"),
        row.getLong("version"),
        row.getObject("created_at", OffsetDateTime.class),
        row.getObject("updated_at", OffsetDateTime.class),
        row.getObject("salary_record_id", UUID.class),
        decimal(row, "annual_base_amount"),
        row.getString("currency_code"),
        row.getObject("salary_effective_from", java.time.LocalDate.class));
  }

  private static String decimal(ResultSet row, String column) throws SQLException {
    var value = row.getBigDecimal(column);
    return value == null ? null : value.toPlainString();
  }

  record EmployeeQuery(
      String search,
      UUID departmentId,
      String countryCode,
      String jobLevel,
      String status,
      int page,
      int size,
      String sort,
      String direction) {}

  record EmployeePage(List<EmployeeSummary> items, long total) {}

  record EmployeeSummary(
      UUID id,
      String employeeNumber,
      String fullName,
      String email,
      UUID departmentId,
      String departmentName,
      String countryCode,
      String jobTitle,
      String jobLevel,
      String employmentStatus,
      long version,
      String annualBaseAmount,
      String currencyCode) {}

  record EmployeeDetails(
      UUID id,
      String employeeNumber,
      String fullName,
      String email,
      UUID departmentId,
      String departmentName,
      String countryCode,
      String jobTitle,
      String jobLevel,
      String employmentStatus,
      long version,
      OffsetDateTime createdAt,
      OffsetDateTime updatedAt,
      UUID salaryRecordId,
      String annualBaseAmount,
      String currencyCode,
      java.time.LocalDate salaryEffectiveFrom) {}
}
