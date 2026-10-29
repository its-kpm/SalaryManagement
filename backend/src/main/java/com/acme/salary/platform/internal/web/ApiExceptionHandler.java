package com.acme.salary.platform.internal.web;

import java.net.URI;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {
  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception exception,
      Object body,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    var problem = problem(status, request);
    if (exception instanceof ErrorResponse error
        && error.getBody().getProperties() != null
        && error.getBody().getProperties().get("code") instanceof String code) {
      problem.setProperty("code", code);
    }
    if (exception instanceof MethodArgumentNotValidException validation) {
      problem.setProperty("code", "VALIDATION_FAILED");
      var fields =
          validation.getBindingResult().getFieldErrors().stream()
              // Do not expose rejected values or interpolated validator messages.
              .map(error -> new FieldError(error.getField(), "INVALID_VALUE", "Invalid value."))
              .distinct()
              .sorted(Comparator.comparing(FieldError::field))
              .toList();
      problem.setProperty("fieldErrors", fields);
    }
    var safeHeaders = new HttpHeaders();
    safeHeaders.putAll(headers); // Preserve protocol headers such as Allow and Accept.
    safeHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
    safeHeaders.setCacheControl("no-store");
    return new ResponseEntity<>(problem, safeHeaders, status);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<Object> unexpected(Exception exception, WebRequest request) {
    // Exception messages and stack traces can contain SQL, credentials, or salary values.
    LOG.atError()
        .addKeyValue("exception.type", exception.getClass().getName())
        .log("Unhandled request failure");
    return handleExceptionInternal(
        exception, null, HttpHeaders.EMPTY, HttpStatus.INTERNAL_SERVER_ERROR, request);
  }

  private ProblemDetail problem(HttpStatusCode status, WebRequest request) {
    var definition =
        switch (status.value()) {
          case 400 -> new Definition("INVALID_REQUEST", "The request is invalid.");
          case 404 -> new Definition("RESOURCE_NOT_FOUND", "The requested resource was not found.");
          case 409 ->
              new Definition("CONFLICT", "The request conflicts with the current resource state.");
          case 405 -> new Definition("METHOD_NOT_ALLOWED", "The HTTP method is not supported.");
          case 406 ->
              new Definition("NOT_ACCEPTABLE", "The requested response format is not supported.");
          case 413 ->
              new Definition("PAYLOAD_TOO_LARGE", "The request exceeds the permitted size.");
          case 415 ->
              new Definition(
                  "UNSUPPORTED_MEDIA_TYPE", "The request content type is not supported.");
          default ->
              new Definition(
                  status.is5xxServerError() ? "INTERNAL_ERROR" : "REQUEST_FAILED",
                  status.is5xxServerError()
                      ? "An unexpected error occurred."
                      : "The request could not be completed.");
        };
    var problem = ProblemDetail.forStatusAndDetail(status, definition.detail());
    var servletRequest = ((ServletWebRequest) request).getRequest();
    problem.setType(URI.create("about:blank"));
    problem.setInstance(URI.create(servletRequest.getRequestURI()));
    problem.setProperty("code", definition.code());
    problem.setProperty(
        "requestId", servletRequest.getAttribute(RequestCorrelationFilter.ATTRIBUTE));
    return problem;
  }

  private record Definition(String code, String detail) {}

  private record FieldError(String field, String code, String message) {}
}
