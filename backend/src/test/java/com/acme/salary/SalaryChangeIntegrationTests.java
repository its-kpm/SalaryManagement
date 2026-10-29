package com.acme.salary;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = {SalaryManagementApplication.class, SalaryChangeIntegrationTests.FixedClock.class})
class SalaryChangeIntegrationTests {
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String PASSWORD = "correct-password-1234";

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder passwords;

  UUID manager;
  UUID seedActor;
  UUID employee;

  @BeforeEach
  void records() {
    jdbc.execute(
        "TRUNCATE audit_events, imports, exchange_rates, exchange_rate_sets, salary_records, employees, departments, countries, currencies, hr_users, spring_session CASCADE");
    manager = UUID.randomUUID();
    seedActor = UUID.randomUUID();
    employee = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO hr_users(id,email,password_hash,role,enabled) VALUES (?,?,?,?,?)",
        manager,
        "manager@example.test",
        passwords.encode(PASSWORD),
        "HR",
        true);
    jdbc.update(
        "INSERT INTO hr_users(id,email,password_hash,role,enabled) VALUES (?,?,?,?,?)",
        seedActor,
        "seed@example.test",
        "not-a-login-hash",
        "HR",
        false);
    var department = UUID.randomUUID();
    jdbc.update("INSERT INTO departments(id,name) VALUES (?,?)", department, "Engineering");
    jdbc.update("INSERT INTO countries(code,name) VALUES (?,?)", "US", "United States");
    jdbc.batchUpdate(
        "INSERT INTO currencies(code,minor_units) VALUES (?,?)",
        java.util.List.of(new Object[] {"USD", 2}, new Object[] {"BHD", 3}));
    jdbc.update(
        "INSERT INTO employees(id,employee_number,email,full_name,department_id,country_code,job_title,job_level,employment_status) VALUES (?,?,?,?,?,?,?,?,?)",
        employee,
        "E-001",
        "employee@example.test",
        "Example Employee",
        department,
        "US",
        "Engineer",
        "L3",
        "ACTIVE");
    jdbc.update(
        "INSERT INTO salary_records(id,employee_id,annual_base_amount,currency_code,effective_from,reason,actor_id) VALUES (?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        employee,
        new BigDecimal("100000.00"),
        "USD",
        Date.valueOf("2025-01-01"),
        "Initial salary",
        seedActor);
  }

  @Test
  void changesSalaryAndReturnsNewestFirstHistoryWithTransactionalAudit() throws Exception {
    try (var session = login()) {
      var changed =
          session.post(
              "/api/v1/employees/" + employee + "/salary-changes",
              change("110000.25", "USD", "2026-10-01", "Annual review", 0));
      assertThat(changed.statusCode()).isEqualTo(201);
      var result = JSON.readTree(changed.body());
      var salaryId = result.get("salaryRecordId").asString();
      assertThat(result.get("annualBaseAmount").asString()).isEqualTo("110000.2500");
      assertThat(result.get("employeeVersion").asLong()).isEqualTo(1);
      assertThat(changed.headers().firstValue("Location").orElse(""))
          .endsWith("/salary-history/" + salaryId);

      assertThat(
              jdbc.queryForObject(
                  "SELECT version FROM employees WHERE id = ?", Long.class, employee))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM salary_records WHERE employee_id = ?",
                  Integer.class,
                  employee))
          .isEqualTo(2);
      assertThat(
              jdbc.queryForObject(
                  "SELECT effective_to FROM salary_records WHERE employee_id = ? AND effective_from = DATE '2025-01-01'",
                  Date.class,
                  employee))
          .isEqualTo(Date.valueOf("2026-10-01"));
      assertThat(
              jdbc.queryForMap(
                  "SELECT actor_id, action, reason, request_id, before_values->>'annualBaseAmount' AS before_amount, after_values->>'annualBaseAmount' AS after_amount FROM audit_events WHERE entity_id = ?",
                  employee))
          .containsEntry("actor_id", manager)
          .containsEntry("action", "SALARY_CHANGED")
          .containsEntry("reason", "Annual review")
          .containsEntry("before_amount", "100000.0000")
          .containsEntry("after_amount", "110000.2500")
          .doesNotContainEntry("request_id", null);

      var history =
          JSON.readTree(
              session
                  .get("/api/v1/employees/" + employee + "/salary-history?size=1&page=0")
                  .body());
      assertThat(history.get("totalElements").asLong()).isEqualTo(2);
      assertThat(history.get("totalPages").asInt()).isEqualTo(2);
      assertThat(history.at("/items/0/id").asString()).isEqualTo(salaryId);
      assertThat(history.at("/items/0/reason").asString()).isEqualTo("Annual review");
    }
  }

  @Test
  void rejectsStaleVersionWithoutAddingAnotherPeriodOrAudit() throws Exception {
    try (var session = login()) {
      assertThat(
              session
                  .post(
                      "/api/v1/employees/" + employee + "/salary-changes",
                      change("105000.00", "USD", "2026-09-01", "First update", 0))
                  .statusCode())
          .isEqualTo(201);
      var stale =
          session.post(
              "/api/v1/employees/" + employee + "/salary-changes",
              change("110000.00", "USD", "2026-10-01", "Stale update", 0));
      assertThat(stale.statusCode()).isEqualTo(409);
      assertThat(stale.body()).contains("STALE_EMPLOYEE_VERSION").doesNotContain("110000");
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM salary_records WHERE employee_id = ?",
                  Integer.class,
                  employee))
          .isEqualTo(2);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events", Integer.class))
          .isEqualTo(1);
    }
  }

  @Test
  void validatesPrecisionDatesOverlapAndEmployeeExistenceBeforeMutation() throws Exception {
    try (var session = login()) {
      assertProblem(
          session.post(
              "/api/v1/employees/" + employee + "/salary-changes",
              change("100000.001", "USD", "2026-10-01", "Too precise", 0)),
          400,
          "INVALID_AMOUNT_PRECISION");
      assertProblem(
          session.post(
              "/api/v1/employees/" + employee + "/salary-changes",
              change("100000.00", "USD", "2026-10-30", "Future change", 0)),
          400,
          "INVALID_EFFECTIVE_DATE");
      assertProblem(
          session.post(
              "/api/v1/employees/" + employee + "/salary-changes",
              change("100000.00", "USD", "2025-01-01", "Same date", 0)),
          409,
          "SALARY_EFFECTIVE_DATE_CONFLICT");
      assertProblem(
          session.post(
              "/api/v1/employees/" + UUID.randomUUID() + "/salary-changes",
              change("100000.00", "USD", "2026-10-01", "Missing employee", 0)),
          404,
          "EMPLOYEE_NOT_FOUND");
      assertThat(
              jdbc.queryForObject(
                  "SELECT version FROM employees WHERE id = ?", Long.class, employee))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM salary_records WHERE employee_id = ?",
                  Integer.class,
                  employee))
          .isEqualTo(1);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events", Integer.class)).isZero();
    }
  }

  @Test
  void rollsBackVersionAndSalaryPeriodsWhenSynchronousAuditAppendFails() throws Exception {
    jdbc.execute(
        """
        CREATE OR REPLACE FUNCTION reject_test_audit() RETURNS trigger
        LANGUAGE plpgsql AS $function$
        BEGIN
          RAISE EXCEPTION 'forced audit failure';
        END
        $function$
        """);
    jdbc.execute(
        "CREATE TRIGGER reject_test_audit BEFORE INSERT ON audit_events FOR EACH ROW EXECUTE FUNCTION reject_test_audit()");
    try (var session = login()) {
      var response =
          session.post(
              "/api/v1/employees/" + employee + "/salary-changes",
              change("110000.00", "USD", "2026-10-01", "Must roll back", 0));
      assertThat(response.statusCode()).isEqualTo(500);
      assertThat(response.body()).contains("INTERNAL_ERROR").doesNotContain("forced audit");
      assertThat(
              jdbc.queryForObject(
                  "SELECT version FROM employees WHERE id = ?", Long.class, employee))
          .isZero();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM salary_records WHERE employee_id = ?",
                  Integer.class,
                  employee))
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT effective_to IS NULL FROM salary_records WHERE employee_id = ?",
                  Boolean.class,
                  employee))
          .isTrue();
      assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events", Integer.class)).isZero();
    } finally {
      jdbc.execute("DROP TRIGGER IF EXISTS reject_test_audit ON audit_events");
      jdbc.execute("DROP FUNCTION IF EXISTS reject_test_audit()");
    }
  }

  private static void assertProblem(
      HttpResponse<String> response, int expectedStatus, String code) {
    assertThat(response.statusCode()).isEqualTo(expectedStatus);
    assertThat(response.headers().firstValue("Content-Type").orElse(""))
        .startsWith("application/problem+json");
    assertThat(response.body()).contains(code).doesNotContain("100000");
  }

  private Session login() throws Exception {
    var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    var client =
        HttpClient.newBuilder()
            .cookieHandler(cookies)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    var initial =
        JSON.readTree(request(client, "GET", "/api/v1/auth/csrf", null, null, null).body());
    var login =
        request(
            client,
            "POST",
            "/api/v1/auth/login",
            "{\"email\":\"manager@example.test\",\"password\":\"" + PASSWORD + "\"}",
            initial.get("headerName").asString(),
            initial.get("token").asString());
    assertThat(login.statusCode()).isEqualTo(200);
    var refreshed =
        JSON.readTree(request(client, "GET", "/api/v1/auth/csrf", null, null, null).body());
    return new Session(
        client, refreshed.get("headerName").asString(), refreshed.get("token").asString());
  }

  private static String change(
      String amount, String currency, String effectiveFrom, String reason, long expectedVersion) {
    return "{\"annualBaseAmount\":\""
        + amount
        + "\",\"currencyCode\":\""
        + currency
        + "\",\"effectiveFrom\":\""
        + effectiveFrom
        + "\",\"reason\":\""
        + reason
        + "\",\"expectedVersion\":"
        + expectedVersion
        + "}";
  }

  private HttpResponse<String> request(
      HttpClient client,
      String method,
      String path,
      String body,
      String csrfHeader,
      String csrfToken)
      throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(5))
            .header("Accept", "application/json");
    if (body != null) request.header("Content-Type", "application/json");
    if (csrfHeader != null) request.header(csrfHeader, csrfToken);
    request.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private final class Session implements AutoCloseable {
    private final HttpClient client;
    private final String csrfHeader;
    private final String csrfToken;

    private Session(HttpClient client, String csrfHeader, String csrfToken) {
      this.client = client;
      this.csrfHeader = csrfHeader;
      this.csrfToken = csrfToken;
    }

    HttpResponse<String> get(String path) throws Exception {
      return request(client, "GET", path, null, null, null);
    }

    HttpResponse<String> post(String path, String body) throws Exception {
      return request(client, "POST", path, body, csrfHeader, csrfToken);
    }

    @Override
    public void close() {
      client.close();
    }
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class FixedClock {
    @Bean
    @Primary
    Clock fixedBusinessClock() {
      return Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC);
    }
  }
}
