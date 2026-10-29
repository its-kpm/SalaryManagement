package com.acme.salary.transfer.internal;

import com.acme.salary.audit.AuditTrail;
import com.acme.salary.compensation.CompensationImportWriter;
import com.acme.salary.employee.EmployeeImportWriter;
import com.acme.salary.employee.EmployeeVersionService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
class ImportApplicationService {
  private static final JsonMapper JSON = JsonMapper.builder().build();

  private final ImportPreviewRepository imports;
  private final EmployeeVersionService versions;
  private final EmployeeImportWriter employees;
  private final CompensationImportWriter compensation;
  private final AuditTrail audits;
  private final Clock clock;

  ImportApplicationService(
      ImportPreviewRepository imports,
      EmployeeVersionService versions,
      EmployeeImportWriter employees,
      CompensationImportWriter compensation,
      AuditTrail audits,
      Clock clock) {
    this.imports = imports;
    this.versions = versions;
    this.employees = employees;
    this.compensation = compensation;
    this.audits = audits;
    this.clock = clock;
  }

  @Transactional
  ImportReceipt apply(UUID previewId, UUID ownerId, UUID requestId) {
    var stored =
        imports
            .lock(previewId)
            .orElseThrow(() -> problem(HttpStatus.NOT_FOUND, "IMPORT_NOT_FOUND"));
    if (!stored.ownerId().equals(ownerId)) {
      throw problem(HttpStatus.NOT_FOUND, "IMPORT_NOT_FOUND");
    }
    if (stored.status().equals("APPLIED")) {
      return JSON.readValue(stored.receipt(), ImportReceipt.class);
    }
    if (!stored.status().equals("VALIDATED")) {
      throw problem(HttpStatus.CONFLICT, "IMPORT_NOT_APPLICABLE");
    }
    if (!stored.expiresAt().isAfter(OffsetDateTime.now(clock))) {
      throw problem(HttpStatus.CONFLICT, "IMPORT_EXPIRED");
    }
    var preview = JSON.readValue(stored.normalizedPreview(), StoredImportPreview.class);
    var rows =
        preview.rows().stream()
            .sorted(Comparator.comparing(row -> UUID.fromString(row.employeeId())))
            .toList();

    for (var row : rows) {
      if (row.action().equals("UPDATE")) {
        var claim =
            versions.claim(UUID.fromString(row.employeeId()), row.expectedVersion().longValue());
        if (claim != EmployeeVersionService.ClaimResult.CLAIMED) {
          throw problem(HttpStatus.CONFLICT, "IMPORT_STALE");
        }
      }
    }

    var resultingVersions = new LinkedHashMap<String, Long>();
    try {
      for (var row : rows) {
        applyRow(previewId, ownerId, requestId, row, resultingVersions);
      }
    } catch (DataIntegrityViolationException conflict) {
      throw problem(HttpStatus.CONFLICT, "IMPORT_STALE");
    }
    var appliedAt = OffsetDateTime.now(clock);
    var receipt =
        new ImportReceipt(
            previewId,
            "APPLIED",
            preview.createCount(),
            preview.updateCount(),
            appliedAt.toString(),
            resultingVersions);
    imports.markApplied(previewId, appliedAt, JSON.writeValueAsString(receipt));
    return receipt;
  }

  private void applyRow(
      UUID previewId,
      UUID actorId,
      UUID requestId,
      NormalizedImportRow row,
      Map<String, Long> resultingVersions) {
    var employeeId = UUID.fromString(row.employeeId());
    var command =
        new EmployeeImportWriter.Command(
            employeeId,
            row.employeeNumber(),
            row.email(),
            row.fullName(),
            UUID.fromString(row.departmentId()),
            row.countryCode(),
            row.jobTitle(),
            row.jobLevel(),
            row.employmentStatus());
    Map<String, Object> before;
    long resultingVersion;
    if (row.action().equals("CREATE")) {
      employees.create(command);
      before = Map.of();
      resultingVersion = 0;
    } else {
      before = employees.updateClaimed(command);
      resultingVersion = row.expectedVersion() + 1;
    }
    var salary =
        compensation.apply(
            employeeId,
            new BigDecimal(row.annualBaseAmount()),
            row.currencyCode(),
            LocalDate.parse(row.effectiveFrom()),
            row.reason(),
            actorId);
    if (salary.status() == CompensationImportWriter.Status.DATE_CONFLICT) {
      throw problem(HttpStatus.CONFLICT, "IMPORT_STALE");
    }
    var after = new LinkedHashMap<>(command.auditValues(resultingVersion));
    after.put("salary", salary.after());
    if (!salary.before().isEmpty()) {
      before = new LinkedHashMap<>(before);
      before.put("salary", salary.before());
    }
    audits.append(
        actorId,
        "EMPLOYEE",
        employeeId,
        row.action().equals("CREATE") ? "IMPORT_CREATED" : "IMPORT_UPDATED",
        before,
        after,
        row.reason(),
        requestId,
        previewId);
    resultingVersions.put(row.employeeNumber(), resultingVersion);
  }

  private static ImportProblem problem(HttpStatus status, String code) {
    return new ImportProblem(status, code);
  }

  record ImportReceipt(
      UUID previewId,
      String status,
      long createdCount,
      long updatedCount,
      String appliedAt,
      Map<String, Long> employeeVersions) {}
}
