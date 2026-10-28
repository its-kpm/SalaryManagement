package com.acme.salary;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.webfixtures.ProbeController;
import com.acme.webfixtures.ProbeSecurityConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({ProbeController.class, ProbeSecurityConfiguration.class})
class ApiErrorContractTests {
  @LocalServerPort private int port;

  @Test
  void validationOmitsRejectedValuesAndInterpolatedMessages() throws Exception {
    var response = send("POST", "/api/v1/probe", "{\"amount\":-123456}", "application/json");
    var problem = assertProblem(response, 400, "VALIDATION_FAILED", "/api/v1/probe");
    assertThat(problem.get("fieldErrors").size()).isEqualTo(1);
    assertThat(problem.get("fieldErrors").get(0).get("field").asString()).isEqualTo("amount");
    assertThat(response.body()).doesNotContain("123456", "Rejected salary");
  }

  @Test
  void malformedJsonDoesNotEchoRequestContents() throws Exception {
    var response = send("POST", "/api/v1/probe", "{secret salary=123456", "application/json");
    assertProblem(response, 400, "INVALID_REQUEST", "/api/v1/probe");
    assertThat(response.body()).doesNotContain("123456", "secret");
  }

  @Test
  void unsupportedContentTypeUsesProblemDetails() throws Exception {
    assertProblem(
        send("POST", "/api/v1/probe", "secret", "text/plain"),
        415,
        "UNSUPPORTED_MEDIA_TYPE",
        "/api/v1/probe");
  }

  @Test
  void unsupportedMethodPreservesAllowHeader() throws Exception {
    var response = send("DELETE", "/api/v1/probe", "", "application/json");
    assertProblem(response, 405, "METHOD_NOT_ALLOWED", "/api/v1/probe");
    assertThat(response.headers().firstValue("Allow"))
        .hasValueSatisfying(value -> assertThat(value).contains("POST"));
  }

  @ParameterizedTest
  @CsvSource({"/api/v1/probe/query", "/api/v1/probe/query?count=secret"})
  void missingAndInvalidParametersUseSafeErrors(String path) throws Exception {
    var response = send("GET", path, "", "application/json");
    assertProblem(response, 400, "INVALID_REQUEST", "/api/v1/probe/query");
    assertThat(response.body()).doesNotContain("secret");
  }

  @Test
  void unknownRouteUsesProblemDetails() throws Exception {
    assertProblem(
        send("GET", "/api/v1/missing", "", "application/json"),
        404,
        "RESOURCE_NOT_FOUND",
        "/api/v1/missing");
  }

  @Test
  void unexpectedFailureDoesNotExposeExceptionDetails() throws Exception {
    var response = send("GET", "/api/v1/probe/failure", "", "application/json");
    assertProblem(response, 500, "INTERNAL_ERROR", "/api/v1/probe/failure");
    assertThat(response.body())
        .doesNotContain("123456", "secret", "SELECT", "IllegalStateException", "trace");
  }

  @Test
  void successfulApiResponsesAreNotCachedAndIdsAreServerGenerated() throws Exception {
    var first = send("POST", "/api/v1/probe", "{\"amount\":1}", "application/json");
    var second = send("POST", "/api/v1/probe", "{\"amount\":2}", "application/json");
    assertThat(first.statusCode()).isEqualTo(200);
    assertThat(first.headers().firstValue("Cache-Control")).contains("no-store");
    assertThat(first.headers().firstValue("X-Request-ID"))
        .isNotEqualTo(second.headers().firstValue("X-Request-ID"));
    UUID.fromString(first.headers().firstValue("X-Request-ID").orElseThrow());
  }

  private JsonNode assertProblem(
      HttpResponse<String> response, int status, String code, String path) {
    assertThat(response.statusCode()).isEqualTo(status);
    assertThat(response.headers().firstValue("Content-Type"))
        .hasValueSatisfying(value -> assertThat(value).startsWith("application/problem+json"));
    assertThat(response.headers().firstValue("Cache-Control")).contains("no-store");
    var json = JsonMapper.builder().build().readTree(response.body());
    assertThat(json.get("type").asString()).isEqualTo("about:blank");
    assertThat(json.get("title").asString()).isNotBlank();
    assertThat(json.get("detail").asString()).isNotBlank();
    assertThat(json.get("status").asInt()).isEqualTo(status);
    assertThat(json.get("code").asString()).isEqualTo(code);
    assertThat(json.get("instance").asString()).isEqualTo(path);
    var id = json.get("requestId").asString();
    UUID.fromString(id);
    assertThat(response.headers().firstValue("X-Request-ID")).contains(id);
    return json;
  }

  private HttpResponse<String> send(String method, String path, String body, String type)
      throws Exception {
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
      var request =
          HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
              .timeout(Duration.ofSeconds(5))
              .header("Accept", "application/json")
              .header("Content-Type", type)
              .header("X-Request-ID", "untrusted-client-value")
              .method(method, HttpRequest.BodyPublishers.ofString(body))
              .build();
      return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
  }
}
