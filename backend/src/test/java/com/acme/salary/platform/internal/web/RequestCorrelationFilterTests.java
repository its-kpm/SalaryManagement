package com.acme.salary.platform.internal.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestCorrelationFilterTests {
  @AfterEach
  void cleanThreadContext() {
    MDC.clear();
  }

  @Test
  void removesRequestContextAfterSuccessfulRequest() throws Exception {
    var request = new MockHttpServletRequest("GET", "/api/v1/employees");
    var response = new MockHttpServletResponse();
    new RequestCorrelationFilter()
        .doFilter(
            request,
            response,
            (req, res) -> {
              assertThat(MDC.get("requestId")).isEqualTo(response.getHeader("X-Request-ID"));
            });
    assertThat(MDC.get("requestId")).isNull();
  }

  @Test
  void restoresPriorContextEvenWhenDownstreamFails() {
    MDC.put("requestId", "outer-context");
    assertThatThrownBy(
            () ->
                new RequestCorrelationFilter()
                    .doFilter(
                        new MockHttpServletRequest("GET", "/api/v1/employees"),
                        new MockHttpServletResponse(),
                        (request, response) -> {
                          throw new ServletException("test failure");
                        }))
        .isInstanceOf(ServletException.class);
    assertThat(MDC.get("requestId")).isEqualTo("outer-context");
  }
}
