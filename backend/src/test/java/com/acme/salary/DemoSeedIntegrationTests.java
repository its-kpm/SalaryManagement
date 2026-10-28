package com.acme.salary;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

@SpringBootTest
class DemoSeedIntegrationTests {
  @Autowired DataSource dataSource;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void resetDatabase() {
    jdbc.execute(
        "TRUNCATE audit_events, imports, exchange_rates, exchange_rate_sets, salary_records, employees, departments, countries, currencies, hr_users CASCADE");
  }

  @Test
  void seedsCompleteReproducibleWorkforceAndReferenceRates() {
    seed();
    assertThat(count("employees")).isEqualTo(10_000);
    assertThat(count("salary_records")).isEqualTo(12_500);
    assertThat(count("departments")).isEqualTo(5);
    assertThat(count("countries")).isEqualTo(4);
    assertThat(count("currencies")).isEqualTo(4);
    assertThat(count("exchange_rates")).isEqualTo(4);
    assertThat(count("current_employee_compensation")).isEqualTo(10_000);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM employees WHERE employment_status = 'ACTIVE'", Integer.class))
        .isEqualTo(9_500);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM current_employee_compensation WHERE salary_record_id IS NULL",
                Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT enabled FROM hr_users WHERE email='seed-actor@demo.invalid'",
                Boolean.class))
        .isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(DISTINCT currency_code) FROM salary_records", Integer.class))
        .isEqualTo(4);
  }

  @Test
  void rerunDoesNotDuplicateRowsOrOverwriteEditedSalary() {
    seed();
    UUID id =
        jdbc.queryForObject(
            "SELECT id FROM salary_records WHERE effective_to IS NULL ORDER BY id LIMIT 1",
            UUID.class);
    jdbc.update(
        "UPDATE salary_records SET annual_base_amount=? WHERE id=?",
        new BigDecimal("123456.78"),
        id);
    seed();
    assertThat(count("employees")).isEqualTo(10_000);
    assertThat(count("salary_records")).isEqualTo(12_500);
    assertThat(
            jdbc.queryForObject(
                "SELECT annual_base_amount FROM salary_records WHERE id=?", BigDecimal.class, id))
        .isEqualByComparingTo("123456.78");
  }

  private int count(String table) {
    // Table names are compile-time constants in this test, never user input.
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
  }

  private void seed() {
    new ResourceDatabasePopulator(new FileSystemResource("../scripts/seed.sql"))
        .execute(dataSource);
  }
}
