package com.acme.salary.employee;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Read-only employee snapshot used to validate a bounded import consistently. */
@Service
public class EmployeeImportCatalog {
  private final JdbcClient jdbc;

  EmployeeImportCatalog(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public Snapshot snapshot() {
    var employees =
        jdbc.sql(
                """
                SELECT e.id, e.employee_number, e.email, e.version, latest.effective_from
                FROM employees e
                LEFT JOIN LATERAL (
                  SELECT effective_from
                  FROM salary_records
                  WHERE employee_id = e.id
                  ORDER BY effective_from DESC, id ASC
                  LIMIT 1
                ) latest ON true
                """)
            .query(
                (row, rowNumber) ->
                    new Employee(
                        row.getObject("id", UUID.class),
                        row.getString("employee_number"),
                        row.getString("email"),
                        row.getLong("version"),
                        row.getObject("effective_from", LocalDate.class)))
            .list();
    return new Snapshot(
        employees.stream()
            .collect(Collectors.toUnmodifiableMap(Employee::employeeNumber, employee -> employee)),
        employees.stream()
            .collect(
                Collectors.toUnmodifiableMap(
                    employee -> employee.email().toLowerCase(Locale.ROOT), employee -> employee)));
  }

  public record Snapshot(
      Map<String, Employee> byEmployeeNumber, Map<String, Employee> byNormalizedEmail) {}

  public record Employee(
      UUID id, String employeeNumber, String email, long version, LocalDate latestSalaryStart) {}
}
