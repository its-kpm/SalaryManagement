package com.acme.salary.compensation.internal;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

final class CompensationProblem extends ErrorResponseException {
  CompensationProblem(HttpStatus status, String code) {
    super(status);
    getBody().setProperty("code", code);
  }
}
