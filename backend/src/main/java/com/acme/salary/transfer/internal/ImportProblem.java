package com.acme.salary.transfer.internal;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;

final class ImportProblem extends ErrorResponseException {
  ImportProblem(HttpStatus status, String code) {
    super(status);
    getBody().setProperty("code", code);
  }
}
