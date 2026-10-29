package com.acme.salary.employee;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Employee write contract for an outer import transaction after preview revalidation. */
@Service
public class EmployeeImportWriter {
  private final JdbcClient jdbc;

  EmployeeImportWriter(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public void create(Command command) {
    jdbc.sql(
            """
            INSERT INTO employees(
                id, employee_number, email, full_name, department_id, country_code,
                job_title, job_level, employment_status)
            VALUES (
                :id, :employeeNumber, :email, :fullName, :departmentId, :countryCode,
                :jobTitle, :jobLevel, :employmentStatus)
            """)
        .param("id", command.id())
        .param("employeeNumber", command.employeeNumber())
        .param("email", command.email())
        .param("fullName", command.fullName())
        .param("departmentId", command.departmentId())
        .param("countryCode", command.countryCode())
        .param("jobTitle", command.jobTitle())
        .param("jobLevel", command.jobLevel())
        .param("employmentStatus", command.employmentStatus())
        .update();
  }

  public Map<String, Object> updateClaimed(Command command) {
    var before =
        jdbc.sql(
                """
                SELECT employee_number, email, full_name, department_id, country_code,
                       job_title, job_level, employment_status, version
                FROM employees WHERE id = :id
                """)
            .param("id", command.id())
            .query(
                (row, rowNumber) -> {
                  var values = new LinkedHashMap<String, Object>();
                  values.put("employeeNumber", row.getString("employee_number"));
                  values.put("email", row.getString("email"));
                  values.put("fullName", row.getString("full_name"));
                  values.put("departmentId", row.getObject("department_id", UUID.class).toString());
                  values.put("countryCode", row.getString("country_code"));
                  values.put("jobTitle", row.getString("job_title"));
                  values.put("jobLevel", row.getString("job_level"));
                  values.put("employmentStatus", row.getString("employment_status"));
                  values.put("employeeVersion", row.getLong("version") - 1);
                  return values;
                })
            .single();
    jdbc.sql(
            """
            UPDATE employees
            SET employee_number = :employeeNumber, email = :email, full_name = :fullName,
                department_id = :departmentId, country_code = :countryCode,
                job_title = :jobTitle, job_level = :jobLevel,
                employment_status = :employmentStatus, updated_at = now()
            WHERE id = :id
            """)
        .param("id", command.id())
        .param("employeeNumber", command.employeeNumber())
        .param("email", command.email())
        .param("fullName", command.fullName())
        .param("departmentId", command.departmentId())
        .param("countryCode", command.countryCode())
        .param("jobTitle", command.jobTitle())
        .param("jobLevel", command.jobLevel())
        .param("employmentStatus", command.employmentStatus())
        .update();
    return before;
  }

  public record Command(
      UUID id,
      String employeeNumber,
      String email,
      String fullName,
      UUID departmentId,
      String countryCode,
      String jobTitle,
      String jobLevel,
      String employmentStatus) {
    public Map<String, Object> auditValues(long version) {
      var values = new LinkedHashMap<String, Object>();
      values.put("employeeNumber", employeeNumber);
      values.put("email", email);
      values.put("fullName", fullName);
      values.put("departmentId", departmentId.toString());
      values.put("countryCode", countryCode);
      values.put("jobTitle", jobTitle);
      values.put("jobLevel", jobLevel);
      values.put("employmentStatus", employmentStatus);
      values.put("employeeVersion", version);
      return values;
    }
  }
}
