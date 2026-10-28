package com.acme.salary.employee.internal;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/employees")
class EmployeeController {
  private final EmployeeDirectoryService directory;

  EmployeeController(EmployeeDirectoryService directory) {
    this.directory = directory;
  }

  @GetMapping
  PageResponse<EmployeeDirectoryRepository.EmployeeSummary> find(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) UUID departmentId,
      @RequestParam(required = false) String countryCode,
      @RequestParam(required = false) String jobLevel,
      @RequestParam(required = false) String employmentStatus,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "25") @Min(1) @Max(100) int size,
      @RequestParam(defaultValue = "fullName") String sort,
      @RequestParam(defaultValue = "asc") String direction) {
    var result =
        directory.find(
            q, departmentId, countryCode, jobLevel, employmentStatus, page, size, sort, direction);
    var pages = result.total() == 0 ? 0 : (int) Math.ceil((double) result.total() / size);
    return new PageResponse<>(result.items(), page, size, result.total(), pages);
  }

  @GetMapping("/{id}")
  EmployeeDirectoryRepository.EmployeeDetails get(@PathVariable UUID id) {
    return directory.get(id);
  }

  record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {}
}
