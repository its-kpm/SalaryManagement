package com.acme.salary;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
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
    classes = {
      SalaryManagementApplication.class,
      ImportApplicationExportIntegrationTests.FixedClock.class
    })
class ImportApplicationExportIntegrationTests {
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String PASSWORD = "correct-password-1234";
  private static final String HEADER =
      "employeeNumber,fullName,email,department,countryCode,jobTitle,jobLevel,employmentStatus,annualBaseAmount,currencyCode,effectiveFrom,reason\r\n";

  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder passwords;

  UUID manager;
  UUID otherManager;
  UUID seedActor;
  UUID employee;
  UUID department;

  @BeforeEach
  void records() {
    jdbc.execute(
        "TRUNCATE audit_events, imports, exchange_rates, exchange_rate_sets, salary_records, employees, departments, countries, currencies, hr_users, spring_session CASCADE");
    manager = hr("manager@example.test", true);
    otherManager = hr("other@example.test", true);
    seedActor = hr("seed@example.test", false);
    employee = UUID.randomUUID();
    department = UUID.randomUUID();
    jdbc.update("INSERT INTO departments(id,name) VALUES (?,?)", department, "Engineering");
    jdbc.batchUpdate(
        "INSERT INTO countries(code,name) VALUES (?,?)",
        java.util.List.of(
            new Object[] {"US", "United States"}, new Object[] {"GB", "United Kingdom"}));
    jdbc.batchUpdate(
        "INSERT INTO currencies(code,minor_units) VALUES (?,?)",
        java.util.List.of(new Object[] {"USD", 2}, new Object[] {"GBP", 2}));
    jdbc.update(
        "INSERT INTO employees(id,employee_number,email,full_name,department_id,country_code,job_title,job_level,employment_status,version) VALUES (?,?,?,?,?,?,?,?,?,?)",
        employee,
        "E-001",
        "existing@example.test",
        "Existing Employee",
        department,
        "US",
        "Engineer",
        "L3",
        "ACTIVE",
        3);
    salary(employee, "100000.00", "USD", "2025-01-01", "Initial salary");
  }

  @Test
  void appliesCreateAndUpdateAtomicallyAndReturnsStoredReceiptOnRetry() throws Exception {
    try (var session = login("manager@example.test")) {
      var previewId = session.validate(validCsv());
      var applied = session.post("/api/v1/imports/" + previewId + "/apply");
      assertThat(applied.statusCode()).isEqualTo(200);
      var receipt = JSON.readTree(applied.body());
      assertThat(receipt.get("status").asString()).isEqualTo("APPLIED");
      assertThat(receipt.get("createdCount").asLong()).isEqualTo(1);
      assertThat(receipt.get("updatedCount").asLong()).isEqualTo(1);
      assertThat(receipt.at("/employeeVersions/E-001").asLong()).isEqualTo(4);
      assertThat(receipt.at("/employeeVersions/E-002").asLong()).isZero();

      assertThat(jdbc.queryForObject("SELECT count(*) FROM employees", Integer.class)).isEqualTo(2);
      assertThat(
              jdbc.queryForMap(
                  "SELECT full_name, job_level, version FROM employees WHERE id = ?", employee))
          .containsEntry("full_name", "Existing Employee Updated")
          .containsEntry("job_level", "L4")
          .containsEntry("version", 4L);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM salary_records", Integer.class))
          .isEqualTo(3);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM audit_events WHERE import_id = ?",
                  Integer.class,
                  previewId))
          .isEqualTo(2);
      assertThat(
              jdbc.queryForObject(
                  "SELECT NOT jsonb_exists(normalized_preview, 'rows') FROM imports WHERE id = ?",
                  Boolean.class,
                  previewId))
          .isTrue();

      var retried = session.post("/api/v1/imports/" + previewId + "/apply");
      assertThat(retried.statusCode()).isEqualTo(200);
      assertThat(JSON.readTree(retried.body())).isEqualTo(receipt);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM employees", Integer.class)).isEqualTo(2);
      assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events", Integer.class))
          .isEqualTo(2);
    }
  }

  @Test
  void rejectsAnotherOwnerAndRollsBackEntireStalePreview() throws Exception {
    UUID previewId;
    try (var owner = login("manager@example.test")) {
      previewId = owner.validate(validCsv());
    }
    try (var other = login("other@example.test")) {
      var hidden = other.post("/api/v1/imports/" + previewId + "/apply");
      assertThat(hidden.statusCode()).isEqualTo(404);
      assertThat(hidden.body()).contains("IMPORT_NOT_FOUND");
    }
    jdbc.update("UPDATE employees SET version = version + 1 WHERE id = ?", employee);
    try (var owner = login("manager@example.test")) {
      var stale = owner.post("/api/v1/imports/" + previewId + "/apply");
      assertThat(stale.statusCode()).isEqualTo(409);
      assertThat(stale.body()).contains("IMPORT_STALE");
    }
    assertThat(jdbc.queryForObject("SELECT count(*) FROM employees", Integer.class)).isEqualTo(1);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM employees WHERE employee_number = 'E-002'", Integer.class))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT full_name FROM employees WHERE id = ?", String.class, employee))
        .isEqualTo("Existing Employee");
    assertThat(jdbc.queryForObject("SELECT count(*) FROM salary_records", Integer.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_events", Integer.class)).isZero();
    assertThat(
            jdbc.queryForObject("SELECT status FROM imports WHERE id = ?", String.class, previewId))
        .isEqualTo("VALIDATED");
  }

  @Test
  void exportsFilteredRfcCsvAndNeutralizesFormulaLeadingText() throws Exception {
    jdbc.update(
        "UPDATE employees SET full_name = ?, job_title = ? WHERE id = ?",
        " \t=2+2",
        "@SUM(A1:A2)",
        employee);
    jdbc.update(
        "UPDATE salary_records SET reason = ? WHERE employee_id = ?",
        "+dangerous reason",
        employee);
    var gbEmployee = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO employees(id,employee_number,email,full_name,department_id,country_code,job_title,job_level,employment_status) VALUES (?,?,?,?,?,?,?,?,?)",
        gbEmployee,
        "E-GB",
        "gb@example.test",
        "GB Employee",
        department,
        "GB",
        "Engineer",
        "L2",
        "ACTIVE");
    salary(gbEmployee, "80000.00", "GBP", "2025-01-01", "Initial");

    try (var anonymous = HttpClient.newHttpClient()) {
      assertThat(get(anonymous, "/api/v1/employees/export").statusCode()).isEqualTo(401);
    }
    try (var session = login("manager@example.test")) {
      var response = session.get("/api/v1/employees/export?countryCode=US");
      assertThat(response.statusCode()).isEqualTo(200);
      assertThat(response.headers().firstValue("Content-Disposition").orElse(""))
          .contains("attachment", "salary-export.csv");
      var format = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true).get();
      try (var csv = format.parse(new StringReader(response.body()))) {
        var records = csv.getRecords();
        assertThat(records).hasSize(1);
        var record = records.getFirst();
        assertThat(record.get("employeeNumber")).isEqualTo("E-001");
        assertThat(record.get("fullName")).isEqualTo("' \t=2+2");
        assertThat(record.get("jobTitle")).isEqualTo("'@SUM(A1:A2)");
        assertThat(record.get("reason")).isEqualTo("'+dangerous reason");
        assertThat(record.get("annualBaseAmount")).isEqualTo("100000.0000");
      }
    }
  }

  private String validCsv() {
    return HEADER
        + "E-001,Existing Employee Updated,existing@example.test,Engineering,US,Senior Engineer,L4,ACTIVE,110000.00,USD,2026-10-01,Annual review\r\n"
        + "E-002,New Employee,new@example.test,Engineering,US,Engineer,L2,ACTIVE,50000.00,USD,2026-01-01,New hire\r\n";
  }

  private UUID hr(String email, boolean enabled) {
    var id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO hr_users(id,email,password_hash,role,enabled) VALUES (?,?,?,?,?)",
        id,
        email,
        enabled ? passwords.encode(PASSWORD) : "not-a-login-hash",
        "HR",
        enabled);
    return id;
  }

  private void salary(
      UUID employeeId, String amount, String currency, String effectiveFrom, String reason) {
    jdbc.update(
        "INSERT INTO salary_records(id,employee_id,annual_base_amount,currency_code,effective_from,reason,actor_id) VALUES (?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        employeeId,
        new BigDecimal(amount),
        currency,
        Date.valueOf(effectiveFrom),
        reason,
        seedActor);
  }

  private Session login(String email) throws Exception {
    var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    var client =
        HttpClient.newBuilder()
            .cookieHandler(cookies)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    var initial = JSON.readTree(get(client, "/api/v1/auth/csrf").body());
    var loginBody =
        ("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}")
            .getBytes(StandardCharsets.UTF_8);
    var login =
        send(
            client,
            "POST",
            "/api/v1/auth/login",
            "application/json",
            loginBody,
            initial.get("headerName").asString(),
            initial.get("token").asString());
    assertThat(login.statusCode()).isEqualTo(200);
    var refreshed = JSON.readTree(get(client, "/api/v1/auth/csrf").body());
    return new Session(
        client, refreshed.get("headerName").asString(), refreshed.get("token").asString());
  }

  private HttpResponse<String> get(HttpClient client, String path) throws Exception {
    return send(client, "GET", path, null, null, null, null);
  }

  private HttpResponse<String> send(
      HttpClient client,
      String method,
      String path,
      String contentType,
      byte[] body,
      String csrfHeader,
      String csrfToken)
      throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(15))
            .header("Accept", "*/*");
    if (contentType != null) builder.header("Content-Type", contentType);
    if (csrfHeader != null) builder.header(csrfHeader, csrfToken);
    builder.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofByteArray(body));
    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
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

    UUID validate(String csv) throws Exception {
      var boundary = "acme-import-boundary-" + UUID.randomUUID();
      var prefix =
          ("--"
                  + boundary
                  + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"employees.csv\"\r\n"
                  + "Content-Type: text/csv\r\n\r\n")
              .getBytes(StandardCharsets.UTF_8);
      var content = csv.getBytes(StandardCharsets.UTF_8);
      var suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
      var body = new ByteArrayOutputStream(prefix.length + content.length + suffix.length);
      body.write(prefix);
      body.write(content);
      body.write(suffix);
      var response =
          send(
              client,
              "POST",
              "/api/v1/imports/validate",
              "multipart/form-data; boundary=" + boundary,
              body.toByteArray(),
              csrfHeader,
              csrfToken);
      assertThat(response.statusCode()).isEqualTo(200);
      var preview = JSON.readTree(response.body());
      assertThat(preview.get("status").asString()).isEqualTo("VALIDATED");
      return UUID.fromString(preview.get("previewId").asString());
    }

    HttpResponse<String> post(String path) throws Exception {
      return send(client, "POST", path, "application/json", new byte[0], csrfHeader, csrfToken);
    }

    HttpResponse<String> get(String path) throws Exception {
      return ImportApplicationExportIntegrationTests.this.get(client, path);
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
    Clock fixedImportClock() {
      return Clock.fixed(Instant.parse("2026-10-29T00:00:00Z"), ZoneOffset.UTC);
    }
  }
}
