package com.acme.salary.employee;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

/** Public employee aggregate contract used by cooperating write modules. */
@Service
public class EmployeeVersionService {
  private final JdbcClient jdbc;

  EmployeeVersionService(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  public ClaimResult claim(UUID employeeId, long expectedVersion) {
    var updated =
        jdbc.sql(
                """
                UPDATE employees
                SET version = version + 1, updated_at = now()
                WHERE id = :id AND version = :expectedVersion
                """)
            .param("id", employeeId)
            .param("expectedVersion", expectedVersion)
            .update();
    if (updated == 1) return ClaimResult.CLAIMED;
    return exists(employeeId) ? ClaimResult.STALE : ClaimResult.NOT_FOUND;
  }

  public boolean exists(UUID employeeId) {
    return jdbc.sql("SELECT EXISTS(SELECT 1 FROM employees WHERE id = :id)")
        .param("id", employeeId)
        .query(Boolean.class)
        .single();
  }

  public enum ClaimResult {
    CLAIMED,
    STALE,
    NOT_FOUND
  }
}
