package com.acme.salary.platform.internal.web;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCompletionLoggingFilterTests {
  @AfterEach
  void clearContext() {
    MDC.clear();
  }

  @Test
  void emitsCorrelatedStructuredMetadataWithoutCompensationContents() throws Exception {
    var logger = (Logger) LoggerFactory.getLogger(RequestCompletionLoggingFilter.class);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    try {
      var request = new MockHttpServletRequest("POST", "/api/v1/employees/employee-1/salary");
      request.setQueryString("reason=TOP-SECRET-RAISE");
      request.setContent(
          "{\"annualBaseAmount\":\"987654.32\",\"reason\":\"TOP-SECRET-RAISE\"}"
              .getBytes(StandardCharsets.UTF_8));
      var response = new MockHttpServletResponse();
      response.setStatus(201);
      MDC.put("requestId", "server-generated-id");

      new RequestCompletionLoggingFilter().doFilter(request, response, (req, res) -> {});

      assertThat(appender.list).hasSize(1);
      var event = appender.list.getFirst();
      assertThat(event.getFormattedMessage()).isEqualTo("Request completed");
      assertThat(event.getMDCPropertyMap()).containsEntry("requestId", "server-generated-id");
      assertThat(event.getKeyValuePairs().toString())
          .contains("http.request.method=\"POST\"")
          .contains("url.path=\"/api/v1/employees/employee-1/salary\"")
          .contains("http.response.status_code=\"201\"")
          .contains("http.server.request.duration_ms=\"")
          .doesNotContain("987654.32", "TOP-SECRET-RAISE");
      assertThat(event.toString()).doesNotContain("987654.32", "TOP-SECRET-RAISE");
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }
}
