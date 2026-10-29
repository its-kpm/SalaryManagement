package com.acme.salary.compensation.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/employees/{employeeId}")
class CompensationController {
  private final CompensationService compensation;

  CompensationController(CompensationService compensation) {
    this.compensation = compensation;
  }

  @GetMapping("/salary-history")
  PageResponse<CompensationRepository.SalaryPeriod> history(
      @PathVariable UUID employeeId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size) {
    var result = compensation.history(employeeId, page, size);
    var pages = result.total() == 0 ? 0 : (int) Math.ceil((double) result.total() / size);
    return new PageResponse<>(result.items(), page, size, result.total(), pages);
  }

  @PostMapping("/salary-changes")
  ResponseEntity<CompensationService.SalaryChangeResult> change(
      @PathVariable UUID employeeId,
      @AuthenticationPrincipal(expression = "id") UUID actorId,
      @Valid @RequestBody SalaryChangeRequest request) {
    var result =
        compensation.change(
            employeeId,
            request.annualBaseAmount(),
            request.currencyCode(),
            request.effectiveFrom(),
            request.reason().strip(),
            request.expectedVersion(),
            actorId,
            UUID.fromString(MDC.get("requestId")));
    var location =
        URI.create(
            "/api/v1/employees/" + employeeId + "/salary-history/" + result.salaryRecordId());
    return ResponseEntity.created(location).body(result);
  }

  record SalaryChangeRequest(
      @NotBlank @Size(max = 24) @Pattern(regexp = "[0-9]{1,15}(?:\\.[0-9]{1,4})?")
          String annualBaseAmount,
      @NotBlank @Pattern(regexp = "[A-Z]{3}") String currencyCode,
      @NotNull LocalDate effectiveFrom,
      @NotBlank @Size(max = 500) String reason,
      @PositiveOrZero long expectedVersion) {}

  record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
}
