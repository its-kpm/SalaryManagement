package com.acme.salary.transfer.internal;

import java.util.List;

record StoredImportPreview(
    int rowCount, long createCount, long updateCount, List<NormalizedImportRow> rows) {}

record NormalizedImportRow(
    int sourceRow,
    String action,
    String employeeId,
    String employeeNumber,
    String fullName,
    String email,
    String departmentId,
    String department,
    String countryCode,
    String jobTitle,
    String jobLevel,
    String employmentStatus,
    String annualBaseAmount,
    String currencyCode,
    String effectiveFrom,
    String reason,
    Long expectedVersion) {}
