package com.acme.salary.employee.internal;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class EmployeeDirectoryService {
  private static final Set<String> STATUSES = Set.of("ACTIVE", "INACTIVE");
  private static final Set<String> DIRECTIONS = Set.of("ASC", "DESC");
  private final EmployeeDirectoryRepository employees;

  EmployeeDirectoryService(EmployeeDirectoryRepository employees) {
    this.employees = employees;
  }

  EmployeeDirectoryRepository.EmployeePage find(
      String q,
      UUID departmentId,
      String countryCode,
      String jobLevel,
      String status,
      int page,
      int size,
      String sort,
      String direction) {
    var search = normalize(q);
    if (search != null && search.length() > 100) badRequest();
    var country = normalize(countryCode);
    if (country != null) {
      country = country.toUpperCase(Locale.ROOT);
      if (!country.matches("[A-Z]{2}")) badRequest();
    }
    var level = normalize(jobLevel);
    var normalizedStatus = normalize(status);
    if (normalizedStatus != null) {
      normalizedStatus = normalizedStatus.toUpperCase(Locale.ROOT);
      if (!STATUSES.contains(normalizedStatus)) badRequest();
    }
    var normalizedDirection = direction.toUpperCase(Locale.ROOT);
    if (!DIRECTIONS.contains(normalizedDirection) || !employees.supportsSort(sort)) badRequest();
    return employees.find(
        new EmployeeDirectoryRepository.EmployeeQuery(
            search,
            departmentId,
            country,
            level,
            normalizedStatus,
            page,
            size,
            sort,
            normalizedDirection));
  }

  EmployeeDirectoryRepository.EmployeeDetails get(UUID id) {
    return employees
        .findById(id)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
  }

  private static String normalize(String value) {
    if (value == null || value.isBlank()) return null;
    return value.strip();
  }

  private static void badRequest() {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid employee query");
  }
}
