package com.acme.salary;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HrAuthenticationIntegrationTests {
  @LocalServerPort int port;
  @Autowired JdbcTemplate jdbc;
  @Autowired PasswordEncoder passwords;
  private static final JsonMapper JSON = JsonMapper.builder().build();

  @BeforeEach
  void createHr() {
    jdbc.execute("DELETE FROM spring_session");
    jdbc.update(
        "DELETE FROM hr_users WHERE email IN (?, ?)",
        "manager@example.test",
        "disabled@example.test");
    jdbc.update(
        "INSERT INTO hr_users(id,email,password_hash,role,enabled) VALUES (?,?,?,?,?)",
        UUID.randomUUID(),
        "manager@example.test",
        passwords.encode("correct-password-1234"),
        "HR",
        true);
    jdbc.update(
        "INSERT INTO hr_users(id,email,password_hash,role,enabled) VALUES (?,?,?,?,?)",
        UUID.randomUUID(),
        "disabled@example.test",
        passwords.encode("correct-password-1234"),
        "HR",
        false);
  }

  @Test
  void loginRequiresCsrfAndPersistsThenInvalidatesSession() throws Exception {
    var cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
    try (var client =
        HttpClient.newBuilder()
            .cookieHandler(cookies)
            .connectTimeout(Duration.ofSeconds(5))
            .build()) {
      assertThat(send(client, "GET", "/api/v1/auth/me", null, null).statusCode()).isEqualTo(401);
      assertThat(send(client, "GET", "/api/v1/employees", null, null).statusCode()).isEqualTo(401);
      var csrf = JSON.readTree(send(client, "GET", "/api/v1/auth/csrf", null, null).body());
      String token = csrf.get("token").asString();
      String header = csrf.get("headerName").asString();
      assertThat(token).isNotBlank();
      assertThat(
              send(
                      client,
                      "POST",
                      "/api/v1/auth/login",
                      login("manager@example.test", "correct-password-1234"),
                      null)
                  .statusCode())
          .isEqualTo(403);
      assertThat(
              send(
                      client,
                      "POST",
                      "/api/v1/auth/login",
                      login("manager@example.test", "wrong-password"),
                      header + ":" + token)
                  .statusCode())
          .isEqualTo(401);
      assertThat(
              send(
                      client,
                      "POST",
                      "/api/v1/auth/login",
                      login("disabled@example.test", "correct-password-1234"),
                      header + ":" + token)
                  .statusCode())
          .isEqualTo(401);
      var login =
          send(
              client,
              "POST",
              "/api/v1/auth/login",
              login("manager@example.test", "correct-password-1234"),
              header + ":" + token);
      assertThat(login.statusCode()).isEqualTo(200);
      assertThat(login.body())
          .contains("manager@example.test", "HR")
          .doesNotContain("password_hash", "correct-password");
      assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session", Integer.class))
          .isPositive();
      assertThat(send(client, "GET", "/api/v1/auth/me", null, null).statusCode()).isEqualTo(200);
      assertThat(send(client, "GET", "/api/v1/employees", null, null).statusCode()).isEqualTo(404);
      var refreshed = JSON.readTree(send(client, "GET", "/api/v1/auth/csrf", null, null).body());
      assertThat(
              send(
                      client,
                      "POST",
                      "/api/v1/auth/logout",
                      "",
                      header + ":" + refreshed.get("token").asString())
                  .statusCode())
          .isEqualTo(204);
      assertThat(send(client, "GET", "/api/v1/auth/me", null, null).statusCode()).isEqualTo(401);
    }
  }

  private static String login(String email, String password) {
    return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
  }

  private HttpResponse<String> send(
      HttpClient client, String method, String path, String body, String csrf) throws Exception {
    var builder =
        HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .timeout(Duration.ofSeconds(5))
            .header("Accept", "application/json");
    if (csrf != null) {
      int separator = csrf.indexOf(':');
      builder.header(csrf.substring(0, separator), csrf.substring(separator + 1));
    }
    if (body != null) builder.header("Content-Type", "application/json");
    builder.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }
}
