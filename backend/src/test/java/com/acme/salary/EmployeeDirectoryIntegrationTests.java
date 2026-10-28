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
class EmployeeDirectoryIntegrationTests {
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String PASSWORD = "correct-password-1234";

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder passwords;

  UUID actor;
  UUID engineering;
  UUID product;
  UUID ada;

  @BeforeEach
  void records() {
    jdbc.execute(
        "TRUNCATE audit_events, imports, exchange_rates, exchange_rate_sets, salary_records, employees, departments, countries, currencies, hr_users, spring_session CASCADE");
    actor = UUID.randomUUID();
    engineering = UUID.randomUUID();
    product = UUID.randomUUID();
    ada = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO hr_users(id,email,password_hash,role) VALUES (?,?,?,?)",
        actor,
        "manager@example.test",
        passwords.encode(PASSWORD),
        "HR");
    jdbc.batchUpdate(
        "INSERT INTO departments(id,name) VALUES (?,?)",
        java.util.List.of(
            new Object[] {engineering, "Engineering"}, new Object[] {product, "Product"}));
    jdbc.batchUpdate(
        "INSERT INTO countries(code,name) VALUES (?,?)",
        java.util.List.of(
            new Object[] {"US", "United States"}, new Object[] {"GB", "United Kingdom"}));
    jdbc.batchUpdate(
        "INSERT INTO currencies(code,minor_units) VALUES (?,?)",
        java.util.List.of(new Object[] {"USD", 2}, new Object[] {"GBP", 2}));
    employee(ada, "E-003", "ada@example.test", "Ada Lovelace", engineering, "GB", "L4", "ACTIVE");
    employee(
        UUID.randomUUID(),
        "E-001",
        "grace@example.test",
        "Grace Hopper",
        engineering,
        "US",
        "L5",
        "ACTIVE");
    employee(
        UUID.randomUUID(),
        "E-002",
        "literal@example.test",
        "Percent % Person",
        product,
        "US",
        "L2",
        "INACTIVE");
    salary(ada, "120000.50", "GBP");
  }

  @Test
  void searchesFiltersSortsAndPaginatesWithStableMetadata() throws Exception {
    try (var client = authenticatedClient()) {
      var response =
          get(
              client,
              "/api/v1/employees?departmentId="
                  + engineering
                  + "&employmentStatus=ACTIVE&sort=employeeNumber&direction=desc&size=1");
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.headers().firstValue("Cache-Control")).hasValue("no-store");
      var page = JSON.readTree(response.body());
      assertThat(page.get("totalElements").asLong()).isEqualTo(2);
      assertThat(page.get("totalPages").asInt()).isEqualTo(2);
      assertThat(page.get("page").asInt()).isZero();
      assertThat(page.get("items").size()).isEqualTo(1);
      assertThat(page.at("/items/0/employeeNumber").asString()).isEqualTo("E-003");
      assertThat(page.at("/items/0/annualBaseAmount").asString()).isEqualTo("120000.5000");
      assertThat(page.at("/items/0/currencyCode").asString()).isEqualTo("GBP");

      var literalWildcard = JSON.readTree(get(client, "/api/v1/employees?q=%25").body());
      assertThat(literalWildcard.get("totalElements").asLong()).isEqualTo(1);
      assertThat(literalWildcard.at("/items/0/fullName").asString()).isEqualTo("Percent % Person");
    }
  }

  @Test
  void returnsProfileAndSafeNotFoundProblem() throws Exception {
    try (var client = authenticatedClient()) {
      var response = get(client, "/api/v1/employees/" + ada);
      assertThat(response.statusCode()).isEqualTo(200);
      var profile = JSON.readTree(response.body());
      assertThat(profile.get("id").asString()).isEqualTo(ada.toString());
      assertThat(profile.get("departmentName").asString()).isEqualTo("Engineering");
      assertThat(profile.get("annualBaseAmount").asString()).isEqualTo("120000.5000");
      assertThat(profile.get("version").asLong()).isZero();

      var missing = get(client, "/api/v1/employees/" + UUID.randomUUID());
      assertThat(missing.statusCode()).isEqualTo(404);
      assertThat(missing.headers().firstValue("Content-Type").orElse(""))
          .startsWith("application/problem+json");
      assertThat(missing.body())
          .contains("RESOURCE_NOT_FOUND")
          .doesNotContain("Employee not found");
    }
  }

  @Test
  void rejectsInvalidDirectoryParameters() throws Exception {
    try (var client = authenticatedClient()) {
      assertBadRequest(client, "/api/v1/employees?size=101");
      assertBadRequest(client, "/api/v1/employees?page=-1");
      assertBadRequest(client, "/api/v1/employees?sort=email");
      assertBadRequest(client, "/api/v1/employees?direction=sideways");
      assertBadRequest(client, "/api/v1/employees?countryCode=USA");
      assertBadRequest(client, "/api/v1/employees?employmentStatus=deleted");
    }
  }

  private void assertBadRequest(HttpClient client, String path) throws Exception {
    var response = get(client, path);
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.body()).contains("INVALID_REQUEST").doesNotContain("exception", "salary");
  }

  private HttpClient authenticatedClient() throws Exception {
    var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    var client =
        HttpClient.newBuilder()
            .cookieHandler(cookies)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    JsonNode csrf = JSON.readTree(get(client, "/api/v1/auth/csrf").body());
    var login =
        request(
            client,
            "POST",
            "/api/v1/auth/login",
            "{\"email\":\"manager@example.test\",\"password\":\"" + PASSWORD + "\"}",
            csrf.get("headerName").asString(),
            csrf.get("token").asString());
    assertThat(login.statusCode()).isEqualTo(200);
    return client;
  }

  private HttpResponse<String> get(HttpClient client, String path) throws Exception {
    return request(client, "GET", path, null, null, null);
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

  private void employee(
      UUID id,
      String number,
      String email,
      String name,
      UUID department,
      String country,
      String level,
      String status) {
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
  }

  private void salary(UUID employee, String amount, String currency) {
    jdbc.update(
        "INSERT INTO salary_records(id,employee_id,annual_base_amount,currency_code,effective_from,reason,actor_id) VALUES (?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        employee,
        new BigDecimal(amount),
        currency,
        Date.valueOf("2020-01-01"),
        "Initial salary",
        actor);
  }
}
