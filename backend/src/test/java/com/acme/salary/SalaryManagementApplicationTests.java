package com.acme.salary;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SalaryManagementApplicationTests {
  @LocalServerPort private int port;

  @Test
  void healthIsAvailableWithoutExposingComponentDetails() throws Exception {
    var response = get("/actuator/health");

    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(response.headers().firstValue("content-type"))
        .hasValueSatisfying(value -> assertThat(value).contains("json"));
    var health = JsonMapper.builder().build().readTree(response.body());
    assertThat(health.get("status").asString()).isEqualTo("UP");
    assertThat(health.has("components")).isFalse();
    assertThat(health.has("details")).isFalse();
  }

  @Test
  void environmentEndpointIsNotExposed() throws Exception {
    assertThat(get("/actuator/env").statusCode()).isEqualTo(401);
  }

  @Test
  void unknownApiDoesNotExposeInternalErrorDetails() throws Exception {
    var response = get("/api/v1/not-implemented");

    assertThat(response.statusCode()).isEqualTo(401);
    assertThat(response.body()).doesNotContain("\"trace\"", "\"exception\"", "\"message\"");
  }

  private HttpResponse<String> get(String path) throws Exception {
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      var request =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
              .timeout(Duration.ofSeconds(5))
              .header("Accept", "application/json")
              .GET()
              .build();
      return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
  }
}
