package com.acme.salary.reporting.internal;

import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
class CompensationReportController {
  private final CompensationReportService reports;

  CompensationReportController(CompensationReportService reports) {
    this.reports = reports;
  }

  @GetMapping("/summary")
  CompensationReportService.ReportResponse summary(
      @RequestParam(required = false) UUID rateSetId,
      @RequestParam(required = false) UUID departmentId,
      @RequestParam(required = false) String countryCode,
      @RequestParam(required = false) String jobLevel) {
    return reports.summary(rateSetId, departmentId, countryCode, jobLevel);
  }

  @GetMapping("/compensation")
  CompensationReportService.ReportResponse breakdown(
      @RequestParam String groupBy,
      @RequestParam(required = false) UUID rateSetId,
      @RequestParam(required = false) UUID departmentId,
      @RequestParam(required = false) String countryCode,
      @RequestParam(required = false) String jobLevel) {
    return reports.breakdown(groupBy, rateSetId, departmentId, countryCode, jobLevel);
  }
}
