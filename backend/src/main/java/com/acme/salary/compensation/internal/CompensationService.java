package com.acme.salary.compensation.internal;

import com.acme.salary.audit.AuditTrail;
import com.acme.salary.employee.EmployeeVersionService;
import com.acme.salary.reference.CurrencyCatalog;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class CompensationService {
  private final CompensationRepository salaries;
  private final EmployeeVersionService employees;
  private final CurrencyCatalog currencies;
  private final AuditTrail audits;
  private final Clock clock;

  CompensationService(
      CompensationRepository salaries,
      EmployeeVersionService employees,
      CurrencyCatalog currencies,
      AuditTrail audits,
      Clock clock) {
    this.salaries = salaries;
    this.employees = employees;
    this.currencies = currencies;
    this.audits = audits;
    this.clock = clock;
  }

  @Transactional
  SalaryChangeResult change(
      UUID employeeId,
      String amountText,
      String currencyCode,
      LocalDate effectiveFrom,
      String reason,
      long expectedVersion,
      UUID actorId,
      UUID requestId) {
    var amount = new BigDecimal(amountText);
    if (amount.signum() <= 0) {
      throw problem(HttpStatus.BAD_REQUEST, "INVALID_SALARY_AMOUNT");
    }
    var minorUnits =
        currencies
            .minorUnits(currencyCode)
            .orElseThrow(() -> problem(HttpStatus.BAD_REQUEST, "UNSUPPORTED_CURRENCY"));
    if (amount.scale() > minorUnits) {
      throw problem(HttpStatus.BAD_REQUEST, "INVALID_AMOUNT_PRECISION");
    }
    if (effectiveFrom.isAfter(LocalDate.now(clock))) {
      throw problem(HttpStatus.BAD_REQUEST, "INVALID_EFFECTIVE_DATE");
    }

    switch (employees.claim(employeeId, expectedVersion)) {
      case NOT_FOUND -> throw problem(HttpStatus.NOT_FOUND, "EMPLOYEE_NOT_FOUND");
      case STALE -> throw problem(HttpStatus.CONFLICT, "STALE_EMPLOYEE_VERSION");
      case CLAIMED -> {
        // Continue while the employee row lock is held by this transaction.
      }
    }

    var previous =
        salaries
            .latestForUpdate(employeeId)
            .orElseThrow(() -> problem(HttpStatus.CONFLICT, "SALARY_HISTORY_MISSING"));
    if (previous.effectiveTo() != null || !effectiveFrom.isAfter(previous.effectiveFrom())) {
      throw problem(HttpStatus.CONFLICT, "SALARY_EFFECTIVE_DATE_CONFLICT");
    }

    salaries.close(previous.id(), effectiveFrom);
    var salaryId =
        salaries.insert(employeeId, amount, currencyCode, effectiveFrom, reason, actorId);
    var newVersion = expectedVersion + 1;
    audits.append(
        actorId,
        "EMPLOYEE",
        employeeId,
        "SALARY_CHANGED",
        snapshot(
            previous.id(),
            previous.annualBaseAmount(),
            previous.currencyCode(),
            previous.effectiveFrom(),
            expectedVersion),
        snapshot(
            salaryId, amount.setScale(4).toPlainString(), currencyCode, effectiveFrom, newVersion),
        reason,
        requestId);
    return new SalaryChangeResult(
        salaryId, amount.setScale(4).toPlainString(), currencyCode, effectiveFrom, newVersion);
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  CompensationRepository.SalaryPage history(UUID employeeId, int page, int size) {
    if (!employees.exists(employeeId)) {
      throw problem(HttpStatus.NOT_FOUND, "EMPLOYEE_NOT_FOUND");
    }
    return salaries.history(employeeId, page, size);
  }

  private static LinkedHashMap<String, Object> snapshot(
      UUID salaryRecordId,
      String amount,
      String currencyCode,
      LocalDate effectiveFrom,
      long employeeVersion) {
    var values = new LinkedHashMap<String, Object>();
    values.put("salaryRecordId", salaryRecordId.toString());
    values.put("annualBaseAmount", amount);
    values.put("currencyCode", currencyCode);
    values.put("effectiveFrom", effectiveFrom.toString());
    values.put("employeeVersion", employeeVersion);
    return values;
  }

  private static CompensationProblem problem(HttpStatus status, String code) {
    return new CompensationProblem(status, code);
  }

  record SalaryChangeResult(
      UUID salaryRecordId,
      String annualBaseAmount,
      String currencyCode,
      LocalDate effectiveFrom,
      long employeeVersion) {}
}
