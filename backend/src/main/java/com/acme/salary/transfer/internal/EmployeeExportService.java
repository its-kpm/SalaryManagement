package com.acme.salary.transfer.internal;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class EmployeeExportService {
  private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");
  private final EmployeeExportRepository employees;

  EmployeeExportService(EmployeeExportRepository employees) {
    this.employees = employees;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  byte[] export(
      String q, UUID departmentId, String countryCode, String jobLevel, String employmentStatus) {
    var country = normalize(countryCode);
    if (country != null) {
      country = country.toUpperCase(Locale.ROOT);
      if (!country.matches("[A-Z]{2}")) invalid();
    }
    var status = normalize(employmentStatus);
    if (status != null) {
      status = status.toUpperCase(Locale.ROOT);
      if (!STATUSES.contains(status)) invalid();
    }
    var search = normalize(q);
    if (search != null && search.length() > 100) invalid();
    var level = normalize(jobLevel);
    if (level != null && level.length() > 50) invalid();
    var rows =
        employees.find(
            new EmployeeExportRepository.Filters(search, departmentId, country, level, status));
    if (rows.size() > 10_000) {
      throw new ImportProblem(HttpStatus.CONTENT_TOO_LARGE, "EXPORT_TOO_LARGE");
    }
    var format =
        CSVFormat.RFC4180.builder().setHeader(CsvImportParser.HEADERS.toArray(String[]::new)).get();
    try (var output = new StringWriter();
        var csv = format.print(output)) {
      for (var row : rows) {
        csv.printRecord(
            safe(row.employeeNumber()),
            safe(row.fullName()),
            safe(row.email()),
            safe(row.department()),
            row.countryCode(),
            safe(row.jobTitle()),
            safe(row.jobLevel()),
            row.employmentStatus(),
            row.annualBaseAmount(),
            row.currencyCode() == null ? "" : row.currencyCode(),
            row.effectiveFrom() == null ? "" : row.effectiveFrom().toString(),
            safe(row.reason()));
      }
      csv.flush();
      return output.toString().getBytes(StandardCharsets.UTF_8);
    } catch (IOException impossible) {
      throw new IllegalStateException("In-memory CSV export failed", impossible);
    }
  }

  static String safe(String value) {
    if (value == null || value.isEmpty()) return "";
    var index = 0;
    while (index < value.length()
        && (Character.isWhitespace(value.charAt(index))
            || Character.isISOControl(value.charAt(index)))) {
      index++;
    }
    if (index < value.length() && "=+-@".indexOf(value.charAt(index)) >= 0) {
      return "'" + value;
    }
    return value;
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) return null;
    return value.strip();
  }

  private static void invalid() {
    throw new ImportProblem(HttpStatus.BAD_REQUEST, "INVALID_EXPORT_FILTER");
  }
}
