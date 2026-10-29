package com.acme.salary;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
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
      CsvImportValidationIntegrationTests.FixedClock.class
    })
class CsvImportValidationIntegrationTests {
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final String PASSWORD = "correct-password-1234";
  private static final String HEADER =
      "employeeNumber,fullName,email,department,countryCode,jobTitle,jobLevel,employmentStatus,annualBaseAmount,currencyCode,effectiveFrom,reason\r\n";

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
    jdbc.update(
        "INSERT INTO departments(id,name) VALUES (?,?)", department, "Research, Development");
    jdbc.update("INSERT INTO countries(code,name) VALUES (?,?)", "US", "United States");
    jdbc.batchUpdate(
        "INSERT INTO currencies(code,minor_units) VALUES (?,?)",
        java.util.List.of(new Object[] {"USD", 2}, new Object[] {"BHD", 3}));
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
  void persistsOwnerBoundValidPreviewWithoutChangingEmployees() throws Exception {
    var csv =
        HEADER
            + "E-001,Existing Employee Updated,existing@example.test,\"Research, Development\",US,Senior Engineer,L4,ACTIVE,110000.00,USD,2026-10-01,Annual review\r\n"
            + "E-002,New Employee,new@example.test,\"Research, Development\",US,Engineer,L2,ACTIVE,50000.123,BHD,2026-01-01,New hire\r\n";
    try (var session = login()) {
      var response = session.upload(csv.getBytes(StandardCharsets.UTF_8));
      assertThat(response.statusCode()).isEqualTo(200);
      var preview = JSON.readTree(response.body());
      assertThat(preview.get("status").asString()).isEqualTo("VALIDATED");
      assertThat(preview.get("rowCount").asInt()).isEqualTo(2);
      assertThat(preview.get("createCount").asLong()).isEqualTo(1);
      assertThat(preview.get("updateCount").asLong()).isEqualTo(1);
      assertThat(preview.get("contentHash").asString()).hasSize(64);
      assertThat(preview.get("expiresAt").asString()).startsWith("2026-10-30T00:00");
      assertThat(preview.get("errors").isEmpty()).isTrue();

      var stored =
          jdbc.queryForMap(
              """
              SELECT owner_id, content_hash, status,
                     normalized_preview->>'rowCount' AS row_count,
                     jsonb_array_length(normalized_preview->'rows') AS stored_rows,
                     expected_versions->>'E-001' AS expected_version
              FROM imports WHERE id = ?
              """,
              UUID.fromString(preview.get("previewId").asString()));
      assertThat(stored)
          .containsEntry("owner_id", manager)
          .containsEntry("status", "VALIDATED")
          .containsEntry("row_count", "2")
          .containsEntry("stored_rows", 2)
          .containsEntry("expected_version", "3");
      assertThat(stored.get("content_hash")).isEqualTo(preview.get("contentHash").asString());
      assertUnchanged();
    }
  }

  @Test
  void persistsUsefulDuplicateAndRowErrorsWithoutStagingInvalidRows() throws Exception {
    var csv =
        HEADER
            + "E-002,First Duplicate,existing@example.test,Unknown,US,Engineer,L2,ACTIVE,100.001,USD,2026-01-01,Import\r\n"
            + "E-002,Second Duplicate,existing@example.test,Unknown,US,Engineer,L2,ACTIVE,100.001,USD,2026-01-01,Import\r\n";
    try (var session = login()) {
      var response = session.upload(csv.getBytes(StandardCharsets.UTF_8));
      assertThat(response.statusCode()).isEqualTo(200);
      var preview = JSON.readTree(response.body());
      assertThat(preview.get("status").asString()).isEqualTo("INVALID");
      assertThat(preview.get("createCount").asLong()).isZero();
      assertThat(preview.get("updateCount").asLong()).isZero();
      var body = response.body();
      assertThat(body)
          .contains(
              "DUPLICATE_IN_FILE",
              "EMAIL_CONFLICT",
              "UNKNOWN_DEPARTMENT",
              "INVALID_AMOUNT_PRECISION")
          .doesNotContain("100.001");
      assertThat(
              jdbc.queryForObject(
                  "SELECT jsonb_array_length(normalized_preview->'rows') FROM imports WHERE id = ?",
                  Integer.class,
                  UUID.fromString(preview.get("previewId").asString())))
          .isZero();
      assertUnchanged();
    }
  }

  @Test
  void enforcesRowAndByteLimits() throws Exception {
    var manyRows = new StringBuilder(HEADER);
    for (int row = 1; row <= 10_001; row++) {
      manyRows
          .append("N-")
          .append(row)
          .append(",Employee ")
          .append(row)
          .append(",employee-")
          .append(row)
          .append(
              "@example.test,\"Research, Development\",US,Engineer,L2,ACTIVE,50000.00,USD,2026-01-01,Import\r\n");
    }
    try (var session = login()) {
      var tooMany = session.upload(manyRows.toString().getBytes(StandardCharsets.UTF_8));
      assertThat(tooMany.statusCode()).isEqualTo(200);
      assertThat(tooMany.body()).contains("INVALID", "TOO_MANY_ROWS");
      assertUnchanged();

      var tooLarge = new byte[10 * 1024 * 1024 + 1];
      java.util.Arrays.fill(tooLarge, (byte) 'x');
      var oversized = session.upload(tooLarge);
      assertThat(oversized.statusCode()).isEqualTo(413);
      assertThat(oversized.body()).contains("IMPORT_FILE_TOO_LARGE");
      assertThat(jdbc.queryForObject("SELECT count(*) FROM imports", Integer.class)).isEqualTo(1);
    }
  }

  private void assertUnchanged() {
    assertThat(jdbc.queryForObject("SELECT count(*) FROM employees", Integer.class)).isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT count(*) FROM salary_records", Integer.class))
        .isEqualTo(1);
    assertThat(
            jdbc.queryForObject("SELECT version FROM employees WHERE id = ?", Long.class, employee))
        .isEqualTo(3);
  }

  private Session login() throws Exception {
    var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    var client =
        HttpClient.newBuilder()
            .cookieHandler(cookies)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    var initial = JSON.readTree(get(client, "/api/v1/auth/csrf").body());
    var login =
        request(
            client,
            "POST",
            "/api/v1/auth/login",
            "application/json",
            ("{\"email\":\"manager@example.test\",\"password\":\"" + PASSWORD + "\"}")
                .getBytes(StandardCharsets.UTF_8),
            initial.get("headerName").asString(),
            initial.get("token").asString());
    assertThat(login.statusCode()).isEqualTo(200);
    var refreshed = JSON.readTree(get(client, "/api/v1/auth/csrf").body());
    return new Session(
        client, refreshed.get("headerName").asString(), refreshed.get("token").asString());
  }

  private HttpResponse<String> get(HttpClient client, String path) throws Exception {
    return request(client, "GET", path, null, null, null, null);
  }

  private HttpResponse<String> request(
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
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/json");
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

    HttpResponse<String> upload(byte[] csv) throws Exception {
      var boundary = "acme-import-boundary-" + UUID.randomUUID();
      var prefix =
          ("--"
                  + boundary
                  + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"employees.csv\"\r\n"
                  + "Content-Type: text/csv\r\n\r\n")
              .getBytes(StandardCharsets.UTF_8);
      var suffix = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
      var multipart = new ByteArrayOutputStream(prefix.length + csv.length + suffix.length);
      multipart.write(prefix);
      multipart.write(csv);
      multipart.write(suffix);
      return request(
          client,
          "POST",
          "/api/v1/imports/validate",
          "multipart/form-data; boundary=" + boundary,
          multipart.toByteArray(),
          csrfHeader,
          csrfToken);
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
