package com.acme.salary.platform.internal.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestCorrelationFilter extends OncePerRequestFilter {
  static final String ATTRIBUTE = RequestCorrelationFilter.class.getName() + ".requestId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    // Generate IDs server-side; never reflect untrusted header values into logs.
    var requestId = UUID.randomUUID().toString();
    request.setAttribute(ATTRIBUTE, requestId);
    response.setHeader("X-Request-ID", requestId);
    if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
      response.setHeader("Cache-Control", "no-store");
    }
    var previous = MDC.get("requestId");
    MDC.put("requestId", requestId);
    try {
      chain.doFilter(request, response);
    } finally {
      if (previous == null) MDC.remove("requestId");
      else MDC.put("requestId", previous);
    }
  }
}
