package com.acme.salary.transfer.internal;

import com.acme.salary.employee.EmployeeImportCatalog;
import com.acme.salary.reference.ImportReferenceData;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
class ImportPreviewWriter {
  private static final JsonMapper JSON = JsonMapper.builder().build();
  private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
  private static final Pattern AMOUNT = Pattern.compile("[0-9]{1,15}(?:\\.[0-9]{1,4})?");

  private final ImportReferenceData references;
  private final EmployeeImportCatalog employees;
  private final ImportPreviewRepository previews;
  private final Clock clock;

  ImportPreviewWriter(
      ImportReferenceData references,
      EmployeeImportCatalog employees,
      ImportPreviewRepository previews,
      Clock clock) {
    this.references = references;
    this.employees = employees;
    this.previews = previews;
    this.clock = clock;
  }

  @Transactional(isolation = Isolation.REPEATABLE_READ)
  ImportPreviewResponse create(UUID ownerId, CsvImportParser.ParsedCsv parsed) {
    var referenceData = references.snapshot();
    var employeeData = employees.snapshot();
    var errors = new LinkedHashSet<>(parsed.errors());
    detectFileDuplicates(parsed.rows(), errors);
    var normalized = new ArrayList<NormalizedImportRow>();
    var expectedVersions = new LinkedHashMap<String, Long>();
    for (var row : parsed.rows()) {
      var value = validate(row, referenceData, employeeData, errors);
      if (value != null) {
        normalized.add(value);
        if (value.expectedVersion() != null) {
          expectedVersions.put(value.employeeNumber(), value.expectedVersion());
        }
      }
    }

    var sortedErrors =
        errors.stream()
            .sorted(
                Comparator.comparingInt(CsvImportParser.ImportError::row)
                    .thenComparing(CsvImportParser.ImportError::field)
                    .thenComparing(CsvImportParser.ImportError::code))
            .toList();
    var valid = sortedErrors.isEmpty();
    var rowsToStore = valid ? List.copyOf(normalized) : List.<NormalizedImportRow>of();
    var createCount =
        valid ? normalized.stream().filter(row -> row.action().equals("CREATE")).count() : 0;
    var updateCount = valid ? normalized.size() - createCount : 0;
    var id = UUID.randomUUID();
    var expiresAt = OffsetDateTime.now(clock).plusHours(24);
    var status = valid ? "VALIDATED" : "INVALID";
    previews.insert(
        id,
        ownerId,
        parsed.contentHash(),
        JSON.writeValueAsString(
            new StoredImportPreview(parsed.rowCount(), createCount, updateCount, rowsToStore)),
        JSON.writeValueAsString(valid ? expectedVersions : Map.of()),
        status,
        JSON.writeValueAsString(sortedErrors),
        expiresAt);
    return new ImportPreviewResponse(
        id,
        status,
        parsed.contentHash(),
        parsed.rowCount(),
        createCount,
        updateCount,
        expiresAt,
        sortedErrors);
  }

  private NormalizedImportRow validate(
      CsvImportParser.RawRow row,
      ImportReferenceData.Snapshot references,
      EmployeeImportCatalog.Snapshot employees,
      LinkedHashSet<CsvImportParser.ImportError> errors) {
    var initialErrors = errors.size();
    var number = required(row, 0, "employeeNumber", 50, errors);
    var fullName = required(row, 1, "fullName", 200, errors);
    var email = required(row, 2, "email", 254, errors).toLowerCase(Locale.ROOT);
    if (!email.isEmpty() && !EMAIL.matcher(email).matches()) {
      error(row, "email", "INVALID_EMAIL", "Enter a valid email address.", errors);
    }
    var departmentName = required(row, 3, "department", 200, errors);
    var department =
        references.departmentsByNormalizedName().get(departmentName.toLowerCase(Locale.ROOT));
    if (!departmentName.isEmpty() && department == null) {
      error(row, "department", "UNKNOWN_DEPARTMENT", "The department is not supported.", errors);
    }
    var country = required(row, 4, "countryCode", 2, errors).toUpperCase(Locale.ROOT);
    if (!country.isEmpty() && !references.countryCodes().contains(country)) {
      error(row, "countryCode", "UNKNOWN_COUNTRY", "The country code is not supported.", errors);
    }
    var jobTitle = required(row, 5, "jobTitle", 200, errors);
    var jobLevel = required(row, 6, "jobLevel", 50, errors);
    var status = required(row, 7, "employmentStatus", 8, errors).toUpperCase(Locale.ROOT);
    if (!status.isEmpty() && !status.equals("ACTIVE") && !status.equals("INACTIVE")) {
      error(
          row, "employmentStatus", "INVALID_EMPLOYMENT_STATUS", "Use ACTIVE or INACTIVE.", errors);
    }
    var amountText = required(row, 8, "annualBaseAmount", 24, errors);
    BigDecimal amount = null;
    if (!amountText.isEmpty() && AMOUNT.matcher(amountText).matches()) {
      amount = new BigDecimal(amountText);
      if (amount.signum() <= 0) {
        error(row, "annualBaseAmount", "INVALID_SALARY_AMOUNT", "Salary must be positive.", errors);
      }
    } else if (!amountText.isEmpty()) {
      error(
          row,
          "annualBaseAmount",
          "INVALID_SALARY_AMOUNT",
          "Use a positive plain decimal amount.",
          errors);
    }
    var currency = required(row, 9, "currencyCode", 3, errors).toUpperCase(Locale.ROOT);
    var minorUnits = references.currencyMinorUnits().get(currency);
    if (!currency.isEmpty() && minorUnits == null) {
      error(row, "currencyCode", "UNKNOWN_CURRENCY", "The currency is not supported.", errors);
    } else if (amount != null && minorUnits != null && amount.scale() > minorUnits) {
      error(
          row,
          "annualBaseAmount",
          "INVALID_AMOUNT_PRECISION",
          "The amount has too many fractional digits for its currency.",
          errors);
    }
    var effectiveText = required(row, 10, "effectiveFrom", 10, errors);
    LocalDate effectiveFrom = null;
    try {
      if (!effectiveText.isEmpty()) effectiveFrom = LocalDate.parse(effectiveText);
    } catch (java.time.format.DateTimeParseException invalid) {
      error(row, "effectiveFrom", "INVALID_DATE", "Use an ISO date in YYYY-MM-DD form.", errors);
    }
    if (effectiveFrom != null && effectiveFrom.isAfter(LocalDate.now(clock))) {
      error(
          row,
          "effectiveFrom",
          "FUTURE_EFFECTIVE_DATE",
          "The effective date cannot be in the future.",
          errors);
    }
    var reason = required(row, 11, "reason", 500, errors);

    var existing = employees.byEmployeeNumber().get(number);
    var emailOwner = employees.byNormalizedEmail().get(email);
    if (emailOwner != null && (existing == null || !emailOwner.id().equals(existing.id()))) {
      error(row, "email", "EMAIL_CONFLICT", "The email belongs to another employee.", errors);
    }
    if (existing != null
        && existing.latestSalaryStart() != null
        && effectiveFrom != null
        && !effectiveFrom.isAfter(existing.latestSalaryStart())) {
      error(
          row,
          "effectiveFrom",
          "SALARY_EFFECTIVE_DATE_CONFLICT",
          "The date must be after the employee's latest salary start.",
          errors);
    }
    if (errors.size() != initialErrors) return null;

    var employeeId = existing == null ? UUID.randomUUID() : existing.id();
    var expectedVersion = existing == null ? null : existing.version();
    return new NormalizedImportRow(
        row.rowNumber(),
        existing == null ? "CREATE" : "UPDATE",
        employeeId.toString(),
        number,
        fullName,
        email,
        department.id().toString(),
        department.name(),
        country,
        jobTitle,
        jobLevel,
        status,
        amount.setScale(4).toPlainString(),
        currency,
        effectiveFrom.toString(),
        reason,
        expectedVersion);
  }

  private static void detectFileDuplicates(
      List<CsvImportParser.RawRow> rows, LinkedHashSet<CsvImportParser.ImportError> errors) {
    var numbers = new HashMap<String, Integer>();
    var emails = new HashMap<String, Integer>();
    for (var row : rows) {
      duplicate(row, row.value(0), "employeeNumber", numbers, errors);
      duplicate(row, row.value(2).toLowerCase(Locale.ROOT), "email", emails, errors);
    }
  }

  private static void duplicate(
      CsvImportParser.RawRow row,
      String value,
      String field,
      Map<String, Integer> firstRows,
      LinkedHashSet<CsvImportParser.ImportError> errors) {
    if (value.isBlank()) return;
    var first = firstRows.putIfAbsent(value, row.rowNumber());
    if (first != null) {
      var message = "The value is duplicated within the file.";
      errors.add(new CsvImportParser.ImportError(first, field, "DUPLICATE_IN_FILE", message));
      errors.add(
          new CsvImportParser.ImportError(row.rowNumber(), field, "DUPLICATE_IN_FILE", message));
    }
  }

  private static String required(
      CsvImportParser.RawRow row,
      int index,
      String field,
      int maxLength,
      LinkedHashSet<CsvImportParser.ImportError> errors) {
    var value = row.value(index);
    if (value.isBlank()) {
      error(row, field, "REQUIRED_FIELD", "A value is required.", errors);
    } else if (value.length() > maxLength) {
      error(row, field, "FIELD_TOO_LONG", "The value exceeds the field limit.", errors);
    }
    return value;
  }

  private static void error(
      CsvImportParser.RawRow row,
      String field,
      String code,
      String message,
      LinkedHashSet<CsvImportParser.ImportError> errors) {
    errors.add(new CsvImportParser.ImportError(row.rowNumber(), field, code, message));
  }

  record ImportPreviewResponse(
      UUID previewId,
      String status,
      String contentHash,
      int rowCount,
      long createCount,
      long updateCount,
      OffsetDateTime expiresAt,
      List<CsvImportParser.ImportError> errors) {}
}
