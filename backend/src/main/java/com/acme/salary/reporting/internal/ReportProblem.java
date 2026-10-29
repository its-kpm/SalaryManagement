package com.acme.salary.reporting.internal;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

final class ReportProblem extends ErrorResponseException {
  ReportProblem(HttpStatus status, String code) {
    super(status);
    getBody().setProperty("code", code);
  }
}
