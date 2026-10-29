package com.acme.salary.platform.internal.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
class RequestCompletionLoggingFilter extends OncePerRequestFilter {
  private static final Logger LOG = LoggerFactory.getLogger(RequestCompletionLoggingFilter.class);

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    var startedAt = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      LOG.atInfo()
          .addKeyValue("http.request.method", request.getMethod())
          // Deliberately omit the query string, headers, principal, and request/response bodies.
          .addKeyValue("url.path", request.getRequestURI())
          .addKeyValue("http.response.status_code", response.getStatus())
          .addKeyValue(
              "http.server.request.duration_ms",
              TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt))
          .log("Request completed");
    }
  }
}
