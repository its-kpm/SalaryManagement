# API conventions

Status: authentication, employee directory/profile reads, salary history/changes, compensation reporting, session authorization, MVC error responses, request IDs, and API no-store headers are implemented. Import endpoints below remain proposed. Prefix business endpoints with /api/v1. JSON requests/responses use camelCase; all endpoints require HR access except login, CSRF-token retrieval, and minimal health.

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

The implemented directory searches `fullName`, `employeeNumber`, and `email` case-insensitively, treating `%`, `_`, and `\` as literal user input rather than SQL wildcard syntax. Supported sort values are `fullName`, `employeeNumber`, `department`, `country`, `jobLevel`, and `currentSalary`; direction is `asc` or `desc`. Current salary may be null. Monetary amounts are serialized as decimal strings to preserve precision in JavaScript clients. Profile lookup returns 404 for an unknown UUID and includes the employee version for future optimistic writes.

Reports accept departmentId, countryCode, jobLevel and rateSetId; they always describe active employees. An omitted rateSetId resolves deterministically to the latest dated set (then lowest UUID), returned explicitly in the response. Responses include its ID, label, asOfDate, and reportingCurrency. Summary returns employee count, total, average, and exact median. Compensation breakdown requires groupBy=`department`, `country`, or `jobLevel` and returns the same metrics per stable key/label. Both execute in a repeatable-read snapshot so totals and counts agree during concurrent writes. No historical-report endpoint is implied.

Conversion multiplies each current local salary by its rate. SQL `NUMERIC` arithmetic and rank-based middle selection avoid floating-point median conversion. Display values round once with HALF_EVEN to the reporting currency's minor units. Empty results return count zero, total zero, and null average/median. If any matching active employee lacks a current salary or rate, the entire response is 422 `REPORT_INCOMPLETE`; values are never silently omitted.

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

The implemented command accepts plain decimal notation only, requires a positive value within `NUMERIC(19,4)` capacity, and rejects fractional precision beyond the selected currency's configured minor units rather than rounding. `effectiveFrom` must be strictly later than the latest salary start and no later than the UTC business date. The command conditionally increments the employee version, closes the prior period, inserts the new period, and appends an audit event in one transaction. Failure of any step rolls everything back. The response is 201 with a `Location`, the new salary record, and `employeeVersion`.

`GET /api/v1/employees/{id}/salary-history` uses zero-based `page` and bounded `size` (default 25, maximum 100), ordered by effective date newest first with ID as a stable tie-breaker. Monetary values remain decimal strings.

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

Current codes include VALIDATION_FAILED, INVALID_REQUEST, RESOURCE_NOT_FOUND, METHOD_NOT_ALLOWED, NOT_ACCEPTABLE, PAYLOAD_TOO_LARGE, UNSUPPORTED_MEDIA_TYPE, INTERNAL_ERROR, employee/salary conflict codes, RATE_SET_NOT_FOUND, INVALID_REPORT_FILTER, INVALID_REPORT_GROUPING, and REPORT_INCOMPLETE; other framework statuses receive REQUEST_FAILED. HTTP 405 responses retain Allow. Unexpected failures log only the correlation ID and exception type, not the exception message or stack trace; this trades diagnostic detail for protection against sensitive values in exceptions.

Salary domain errors preserve stable codes while the shared advice replaces details with status-level safe messages, preventing submitted compensation values from appearing in error responses. Reporting errors remain future work. Advice handles MVC exceptions, not arbitrary servlet-container or security-filter failures. Error responses and `/api/` responses have no-store headers. MDC request context is cleaned up even when downstream processing fails.

## Browser and export behavior

Use same-origin cookies and CSRF headers; do not enable wildcard credentialed CORS. Send Cache-Control: no-store on sensitive API responses. Keep session cookies HttpOnly; the CSRF token is separately available to the frontend.

CSV export uses proper quoting and UTF-8; neutralize formula-leading untrusted text cells, including dangerous prefixes after whitespace/control characters. Export only authorized fields and impose an explicit maximum matching the initial 10,000-row scope. Return an actionable error if limits are exceeded.

## Authentication implemented in Commit 7

Use a same-origin browser client. Obtain `GET /api/v1/auth/csrf` before login; keep the returned session cookie and send the returned token under its `headerName` for POST login and logout. Refresh the CSRF token after login. `POST /api/v1/auth/login` accepts JSON `email` and `password`, responds with `id`, `email`, `role`, and changes the session ID. `/auth/me` needs an HR session. `POST /auth/logout` invalidates it. An unauthenticated API request receives 401; no authorization data comes from the client. Login failures use a generic 401; disabled accounts cannot log in. The application does not ship credentials. Nonlocal session cookies default to HttpOnly, SameSite=Lax, Secure.

Spring Security generates its own 401/403 JSON with a server request ID. Remaining MVC errors use the shared Problem Details advice. Rate limiting and enterprise SSO are deployment follow-ups; the demo is not exposed publicly yet.
