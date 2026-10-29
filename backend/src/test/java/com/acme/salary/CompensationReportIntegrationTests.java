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
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CompensationReportIntegrationTests {
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String PASSWORD = "correct-password-1234";

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder passwords;

  UUID manager;
  UUID seedActor;
  UUID engineering;
  UUID product;
  UUID rateSet;

  @BeforeEach
  void records() {
    jdbc.execute(
        "TRUNCATE audit_events, imports, exchange_rates, exchange_rate_sets, salary_records, employees, departments, countries, currencies, hr_users, spring_session CASCADE");
    manager = UUID.randomUUID();
    seedActor = UUID.randomUUID();
    engineering = UUID.randomUUID();
    product = UUID.randomUUID();
    rateSet = UUID.randomUUID();
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
    jdbc.batchUpdate(
        "INSERT INTO departments(id,name) VALUES (?,?)",
        java.util.List.of(
            new Object[] {engineering, "Engineering"}, new Object[] {product, "Product"}));
    jdbc.batchUpdate(
        "INSERT INTO countries(code,name) VALUES (?,?)",
        java.util.List.of(
            new Object[] {"US", "United States"},
            new Object[] {"GB", "United Kingdom"},
            new Object[] {"DE", "Germany"}));
    jdbc.batchUpdate(
        "INSERT INTO currencies(code,minor_units) VALUES (?,?)",
        java.util.List.of(
            new Object[] {"USD", 2}, new Object[] {"GBP", 2}, new Object[] {"EUR", 2}));
    jdbc.update(
        "INSERT INTO exchange_rate_sets(id,reporting_currency,as_of_date,label) VALUES (?,?,?,?)",
        rateSet,
        "USD",
        Date.valueOf("2026-01-01"),
        "Known test rates");
    jdbc.batchUpdate(
        "INSERT INTO exchange_rates(rate_set_id,source_currency,rate) VALUES (?,?,?)",
        java.util.List.of(
            new Object[] {rateSet, "USD", new BigDecimal("1.000000000000")},
            new Object[] {rateSet, "GBP", new BigDecimal("1.250000000000")}));

    createEmployee(
        "E-001",
        "a@example.test",
        "A Employee",
        engineering,
        "US",
        "L2",
        "ACTIVE",
        "100.0050",
        "USD");
    createEmployee(
        "E-002",
        "b@example.test",
        "B Employee",
        engineering,
        "GB",
        "L2",
        "ACTIVE",
        "100.0000",
        "GBP");
    createEmployee(
        "E-003", "c@example.test", "C Employee", product, "US", "L3", "ACTIVE", "200.0000", "USD");
    createEmployee(
        "E-004",
        "d@example.test",
        "D Employee",
        product,
        "US",
        "L3",
        "INACTIVE",
        "999.0000",
        "USD");
  }

  @Test
  void calculatesExactSummaryFilterMedianAndHalfEvenRounding() throws Exception {
    try (var client = login()) {
      var response = get(client, "/api/v1/reports/summary");
      assertThat(response.statusCode()).isEqualTo(200);
      var report = JSON.readTree(response.body());
      assertThat(report.at("/rateSet/id").asString()).isEqualTo(rateSet.toString());
      assertThat(report.at("/rateSet/reportingCurrency").asString()).isEqualTo("USD");
      assertThat(report.at("/rateSet/asOfDate").asString()).isEqualTo("2026-01-01");
      assertThat(report.at("/rateSet/label").asString()).isEqualTo("Known test rates");
      assertMetrics(report.get("summary"), 3, "425.00", "141.67", "125.00");

      var filtered =
          JSON.readTree(
              get(client, "/api/v1/reports/summary?rateSetId=" + rateSet + "&countryCode=US")
                  .body());
      assertMetrics(filtered.get("summary"), 2, "300.00", "150.00", "150.00");

      var empty =
          JSON.readTree(get(client, "/api/v1/reports/summary?countryCode=IN").body())
              .get("summary");
      assertThat(empty.get("employeeCount").asLong()).isZero();
      assertThat(empty.get("totalAnnualBase").asString()).isEqualTo("0.00");
      assertThat(empty.get("averageAnnualBase").isNull()).isTrue();
      assertThat(empty.get("medianAnnualBase").isNull()).isTrue();
    }
  }

  @Test
  void breaksDownByDepartmentWithStableKeysAndExactEvenMedian() throws Exception {
    try (var client = login()) {
      var response =
          get(client, "/api/v1/reports/compensation?groupBy=department&rateSetId=" + rateSet);
      assertThat(response.statusCode()).isEqualTo(200);
      var groups = JSON.readTree(response.body()).get("groups");
      assertThat(groups.size()).isEqualTo(2);
      assertThat(groups.get(0).get("key").asString()).isEqualTo(engineering.toString());
      assertThat(groups.get(0).get("label").asString()).isEqualTo("Engineering");
      assertMetrics(groups.get(0).get("metrics"), 2, "225.00", "112.50", "112.50");
      assertThat(groups.get(1).get("key").asString()).isEqualTo(product.toString());
      assertMetrics(groups.get(1).get("metrics"), 1, "200.00", "200.00", "200.00");
    }
  }

  @Test
  void rejectsIncompleteReportsWhenAnyActiveSalaryHasNoRate() throws Exception {
    createEmployee(
        "E-005", "e@example.test", "E Employee", product, "DE", "L3", "ACTIVE", "100.0000", "EUR");
    try (var client = login()) {
      var response = get(client, "/api/v1/reports/summary?rateSetId=" + rateSet);
      assertThat(response.statusCode()).isEqualTo(422);
      assertThat(response.body()).contains("REPORT_INCOMPLETE").doesNotContain("100.0000");
      var invalid = get(client, "/api/v1/reports/compensation?groupBy=salary");
      assertThat(invalid.statusCode()).isEqualTo(400);
      assertThat(invalid.body()).contains("INVALID_REPORT_GROUPING");
    }
  }

  private static void assertMetrics(
      JsonNode metrics, long count, String total, String average, String median) {
    assertThat(metrics.get("employeeCount").asLong()).isEqualTo(count);
    assertThat(metrics.get("totalAnnualBase").asString()).isEqualTo(total);
    assertThat(metrics.get("averageAnnualBase").asString()).isEqualTo(average);
    assertThat(metrics.get("medianAnnualBase").asString()).isEqualTo(median);
  }

  private void createEmployee(
      String number,
      String email,
      String name,
      UUID department,
      String country,
      String level,
      String status,
      String amount,
      String currency) {
    var id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO employees(id,employee_number,email,full_name,department_id,country_code,job_title,job_level,employment_status) VALUES (?,?,?,?,?,?,?,?,?)",
        id,
        number,
        email,
        name,
        department,
        country,
        "Engineer",
        level,
        status);
    jdbc.update(
        "INSERT INTO salary_records(id,employee_id,annual_base_amount,currency_code,effective_from,reason,actor_id) VALUES (?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        id,
        new BigDecimal(amount),
        currency,
        Date.valueOf("2020-01-01"),
        "Test salary",
        seedActor);
  }

  private HttpClient login() throws Exception {
    var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    var client =
        HttpClient.newBuilder()
            .cookieHandler(cookies)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    var csrf = JSON.readTree(get(client, "/api/v1/auth/csrf").body());
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1/auth/login"))
            .timeout(Duration.ofSeconds(5))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .header(csrf.get("headerName").asString(), csrf.get("token").asString())
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    "{\"email\":\"manager@example.test\",\"password\":\"" + PASSWORD + "\"}"))
            .build();
    assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode())
        .isEqualTo(200);
    return client;
  }

  private HttpResponse<String> get(HttpClient client, String path) throws Exception {
    var request =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(5))
            .header("Accept", "application/json")
            .GET()
            .build();
    return client.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
