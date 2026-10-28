package com.acme.salary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.sql.Date;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class SchemaIntegrationTests {
  @Autowired JdbcTemplate jdbc;
  UUID actor;
  UUID department;
  UUID employee;

  @BeforeEach
  void baseRecords() {
    jdbc.execute(
        "TRUNCATE audit_events, imports, exchange_rates, exchange_rate_sets, salary_records, employees, departments, countries, currencies, hr_users CASCADE");
    actor = UUID.randomUUID();
    department = UUID.randomUUID();
    employee = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO hr_users(id,email,password_hash,role) VALUES (?,?,?,?)",
        actor,
        "hr@example.test",
        "placeholder",
        "HR");
    jdbc.update("INSERT INTO departments(id,name) VALUES (?,?)", department, "Engineering");
    jdbc.update("INSERT INTO countries(code,name) VALUES (?,?)", "US", "United States");
    jdbc.update("INSERT INTO currencies(code,minor_units) VALUES (?,?)", "USD", 2);
    jdbc.update(
        "INSERT INTO employees(id,employee_number,email,full_name,department_id,country_code,job_title,job_level,employment_status) VALUES (?,?,?,?,?,?,?,?,?)",
        employee,
        "E-001",
        "employee@example.test",
        "Example Employee",
        department,
        "US",
        "Engineer",
        "L2",
        "ACTIVE");
  }

  @Test
  void migratesSchemaAndExposesCurrentSalaryReadView() {
    salary("2020-01-01", null, "100000.00", "Annual review");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT annual_base_amount FROM current_employee_compensation WHERE employee_id = ?",
                BigDecimal.class,
                employee))
        .isEqualByComparingTo("100000.00");
  }

  @Test
  void preventsOverlappingAndDuplicateOpenSalaryPeriods() {
    salary("2020-01-01", "2022-01-01", "90000", "Initial");
    salary("2022-01-01", null, "100000", "Annual review");
    assertThatThrownBy(() -> salary("2021-01-01", "2023-01-01", "110000", "Overlap"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> salary("2023-01-01", null, "120000", "Second open period"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void rejectsNegativeSalaryAndInvalidDateRange() {
    assertThatThrownBy(() -> salary("2020-01-01", null, "-1", "Invalid"))
        .isInstanceOf(DataIntegrityViolationException.class);
    assertThatThrownBy(() -> salary("2022-01-01", "2022-01-01", "90000", "Invalid"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  void enforcesEmployeeAndExchangeRateIntegrity() {
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO employees(id,employee_number,email,full_name,department_id,country_code,job_title,job_level,employment_status) VALUES (?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(),
                    "E-001",
                    "duplicate@example.test",
                    "Duplicate",
                    department,
                    "US",
                    "Engineer",
                    "L2",
                    "ACTIVE"))
        .isInstanceOf(DataIntegrityViolationException.class);
    var rateSet = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO exchange_rate_sets(id,reporting_currency,as_of_date,label) VALUES (?,?,?,?)",
        rateSet,
        "USD",
        Date.valueOf("2026-01-01"),
        "Demo");
    assertThatThrownBy(
            () ->
                jdbc.update(
                    "INSERT INTO exchange_rates(rate_set_id,source_currency,rate) VALUES (?,?,?)",
                    rateSet,
                    "USD",
                    BigDecimal.ZERO))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private void salary(String start, String end, String amount, String reason) {
    jdbc.update(
        "INSERT INTO salary_records(id,employee_id,annual_base_amount,currency_code,effective_from,effective_to,reason,actor_id) VALUES (?,?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        employee,
        new BigDecimal(amount),
        "USD",
        Date.valueOf(start),
        end == null ? null : Date.valueOf(end),
        reason,
        actor);
  }
}
