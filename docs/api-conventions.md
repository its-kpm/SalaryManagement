# API conventions

Status: shared MVC error responses, request IDs, and API no-store headers are implemented. Business endpoints below remain proposed and are not implemented yet. Prefix business endpoints with /api/v1. JSON requests/responses use camelCase; all endpoints require HR access except login, CSRF-token retrieval, and minimal health.

## Planned routes

| Method | Route | Behavior |
|---|---|---|
| GET | /api/v1/auth/csrf | Obtain CSRF token for unsafe requests |
| POST | /api/v1/auth/login | Authenticate; rotate session and return user summary |
| POST | /api/v1/auth/logout | Invalidate session |
| GET | /api/v1/auth/me | Current authenticated user |
| GET | /api/v1/reference-data | Supported countries, currencies, departments, levels |
| GET | /api/v1/employees | Search/filter/sort/page |
| GET | /api/v1/employees/{id} | Employee profile, current salary, employee version |
| GET | /api/v1/employees/{id}/salary-history | Paginated salary periods, newest first |
| POST | /api/v1/employees/{id}/salary-changes | Append effective-dated compensation; return 201 and Location |
| GET | /api/v1/reports/summary | Count, annual cost, average, median |
| GET | /api/v1/reports/compensation | Group by department, country, or jobLevel |
| POST | /api/v1/imports/validate | Multipart CSV; return preview ID, errors, expiry, proposed counts |
| POST | /api/v1/imports/{id}/apply | Apply stored validated preview; repeated success returns same receipt |
| GET | /api/v1/employees/export | CSV for matching filters, not just the current page |

Employee creation and supported profile updates initially occur through CSV; dedicated employee editing endpoints are outside this first contract. Imports include employeeNumber, fullName, email, department, countryCode, jobTitle, jobLevel, employmentStatus, annualBaseAmount, currencyCode, effectiveFrom, and reason. Exact template and field limits will be frozen with importer tests.

## Pagination and filters

Directory parameters: q, departmentId, countryCode, jobLevel, employmentStatus, page (zero-based), size (default 25, maximum 100), sort, direction. Reject invalid values. Allowlist sort fields and parameterize SQL; always add id as a stable tie-breaker. Return items, page, size, totalElements, totalPages. Whitespace-only search means no search.

Reports accept departmentId, countryCode, jobLevel and rateSetId; they always describe active employees. An omitted rateSetId resolves to one configured default, returned explicitly in the response. Include asOfDate and reportingCurrency. Execute related aggregates in one consistent database snapshot so totals and counts agree during concurrent writes. No historical-report endpoint is implied.

## Salary command

```json
{
  "annualBaseAmount": "125000.00",
  "currencyCode": "USD",
  "effectiveFrom": "2026-10-28",
  "reason": "Annual compensation review",
  "expectedVersion": 7
}
```

The effective date above is illustrative. Validate against the injected business clock. Derive actor from the session, never a client-supplied actorId. Return the new salary record and updated employeeVersion. A retry with the old version conflicts; clients should reload before resubmitting.

## Errors

Use RFC 9457 application/problem+json with type, title, status, detail, instance, plus stable code, requestId, and optional fieldErrors. Never return SQL, stack traces, or submitted salary values in error details.

| Status | Meaning |
|---|---|
| 400 | Malformed input or validation failure |
| 401 | Missing/expired authentication or invalid credentials |
| 403 | Insufficient role or invalid CSRF token |
| 404 | Missing resource; also use for another user's import |
| 409 | Stale version, overlapping period, or import invalidated since preview |
| 413 | Upload or row-count limit exceeded |
| 415 | Unsupported request content type |
| 422 | Incomplete report due to missing salary or FX reference data |
| 500 | Unexpected failure with safe message and correlation ID |

An inspectable CSV validation result can return 200 with status INVALID and row errors; it performs no employee writes and cannot be applied. Apply is transactionally atomic, revalidates preview expiry/data, and returns 409 for expired or invalidated previews. Validation requests may create separate previews; only application of an individual preview is idempotent.

## Implemented error foundation

The platform module handles MVC input/validation errors, missing routes, unsupported methods/media types, and unexpected controller failures. Responses use `about:blank`, the standard HTTP title, status, safe detail, request path without query parameters, code, and requestId. `X-Request-ID` carries the same server-generated UUID; incoming IDs are not trusted. Field errors use field, code=INVALID_VALUE, and a generic message, avoiding validator-message interpolation of submitted values.

Current codes: VALIDATION_FAILED, INVALID_REQUEST, RESOURCE_NOT_FOUND, METHOD_NOT_ALLOWED, NOT_ACCEPTABLE, PAYLOAD_TOO_LARGE, UNSUPPORTED_MEDIA_TYPE, and INTERNAL_ERROR; other framework statuses receive REQUEST_FAILED. HTTP 405 responses retain Allow. Unexpected failures log only the correlation ID and exception type, not the exception message or stack trace; this trades diagnostic detail for protection against sensitive values in exceptions.

Domain conflict/reporting errors and authentication/CSRF errors will be implemented and tested with those features. Advice handles MVC exceptions, not arbitrary servlet-container or future security-filter failures. Error responses and `/api/` responses have no-store headers. MDC request context is cleaned up even when downstream processing fails.

## Browser and export behavior

Use same-origin cookies and CSRF headers; do not enable wildcard credentialed CORS. Send Cache-Control: no-store on sensitive API responses. Keep session cookies HttpOnly; the CSRF token is separately available to the frontend.

CSV export uses proper quoting and UTF-8; neutralize formula-leading untrusted text cells, including dangerous prefixes after whitespace/control characters. Export only authorized fields and impose an explicit maximum matching the initial 10,000-row scope. Return an actionable error if limits are exceeded.
