package com.acme.salary.transfer.internal;

import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employees")
class EmployeeExportController {
  private final EmployeeExportService exports;

  EmployeeExportController(EmployeeExportService exports) {
    this.exports = exports;
  }

  @GetMapping(value = "/export", produces = "text/csv")
  ResponseEntity<byte[]> export(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) UUID departmentId,
      @RequestParam(required = false) String countryCode,
      @RequestParam(required = false) String jobLevel,
      @RequestParam(required = false) String employmentStatus) {
    var content = exports.export(q, departmentId, countryCode, jobLevel, employmentStatus);
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition.attachment().filename("salary-export.csv").build().toString())
        .body(content);
  }
}
