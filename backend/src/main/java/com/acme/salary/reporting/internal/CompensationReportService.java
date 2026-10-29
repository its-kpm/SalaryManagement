package com.acme.salary.reporting.internal;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class CompensationReportService {
  private final CompensationReportRepository reports;

  CompensationReportService(CompensationReportRepository reports) {
    this.reports = reports;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  ReportResponse summary(UUID rateSetId, UUID departmentId, String countryCode, String jobLevel) {
    var rateSet = resolveRateSet(rateSetId);
    var metrics = reports.summary(rateSet.id(), filters(departmentId, countryCode, jobLevel));
    requireComplete(metrics);
    return new ReportResponse(rateSet(rateSet), values(metrics, rateSet.minorUnits()), List.of());
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  ReportResponse breakdown(
      String groupBy, UUID rateSetId, UUID departmentId, String countryCode, String jobLevel) {
    var grouping = parseGroupBy(groupBy);
    var rateSet = resolveRateSet(rateSetId);
    var filters = filters(departmentId, countryCode, jobLevel);
    var summary = reports.summary(rateSet.id(), filters);
    requireComplete(summary);
    var groups =
        reports.breakdown(rateSet.id(), filters, grouping).stream()
            .map(
                group ->
                    new GroupResponse(
                        group.key(), group.label(), values(group, rateSet.minorUnits())))
            .toList();
    return new ReportResponse(rateSet(rateSet), values(summary, rateSet.minorUnits()), groups);
  }

  private CompensationReportRepository.RateSet resolveRateSet(UUID rateSetId) {
    return reports
        .resolveRateSet(rateSetId)
        .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "RATE_SET_NOT_FOUND"));
  }

  private static CompensationReportRepository.Filters filters(
      UUID departmentId, String countryCode, String jobLevel) {
    var country = normalize(countryCode);
    if (country != null) {
      country = country.toUpperCase(Locale.ROOT);
      if (!country.matches("[A-Z]{2}")) {
        throw problem(HttpStatus.BAD_REQUEST, "INVALID_REPORT_FILTER");
      }
    }
    var level = normalize(jobLevel);
    if (level != null && level.length() > 50) {
      throw problem(HttpStatus.BAD_REQUEST, "INVALID_REPORT_FILTER");
    }
    return new CompensationReportRepository.Filters(departmentId, country, level);
  }

  private static CompensationReportRepository.GroupBy parseGroupBy(String groupBy) {
    return switch (groupBy) {
      case "department" -> CompensationReportRepository.GroupBy.DEPARTMENT;
      case "country" -> CompensationReportRepository.GroupBy.COUNTRY;
      case "jobLevel" -> CompensationReportRepository.GroupBy.JOB_LEVEL;
      default -> throw problem(HttpStatus.BAD_REQUEST, "INVALID_REPORT_GROUPING");
    };
  }

  private static void requireComplete(CompensationReportRepository.Metrics metrics) {
    if (metrics.expectedCount() != metrics.completeCount()) {
      throw problem(HttpStatus.UNPROCESSABLE_CONTENT, "REPORT_INCOMPLETE");
    }
  }

  private static RateSetResponse rateSet(CompensationReportRepository.RateSet rateSet) {
    return new RateSetResponse(
        rateSet.id(), rateSet.reportingCurrency(), rateSet.asOfDate(), rateSet.label());
  }

  private static MetricResponse values(
      CompensationReportRepository.Metrics metrics, int minorUnits) {
    return new MetricResponse(
        metrics.employeeCount(),
        money(metrics.total(), minorUnits, true),
        money(metrics.average(), minorUnits, false),
        money(metrics.median(), minorUnits, false));
  }

  private static MetricResponse values(
      CompensationReportRepository.GroupMetrics metrics, int minorUnits) {
    return new MetricResponse(
        metrics.employeeCount(),
        money(metrics.total(), minorUnits, true),
        money(metrics.average(), minorUnits, false),
        money(metrics.median(), minorUnits, false));
  }

  private static String money(BigDecimal value, int minorUnits, boolean zeroWhenNull) {
    if (value == null)
      return zeroWhenNull ? BigDecimal.ZERO.setScale(minorUnits).toPlainString() : null;
    return value.setScale(minorUnits, RoundingMode.HALF_EVEN).toPlainString();
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) return null;
    return value.strip();
  }

  private static ReportProblem problem(HttpStatus status, String code) {
    return new ReportProblem(status, code);
  }

  record ReportResponse(
      RateSetResponse rateSet, MetricResponse summary, List<GroupResponse> groups) {}

  record RateSetResponse(
      UUID id, String reportingCurrency, java.time.LocalDate asOfDate, String label) {}

  record MetricResponse(
      long employeeCount,
      String totalAnnualBase,
      String averageAnnualBase,
      String medianAnnualBase) {}

  record GroupResponse(String key, String label, MetricResponse metrics) {}
}
