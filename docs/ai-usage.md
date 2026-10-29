# AI assistance log

This log records actual assistance and checks, not hypothetical future testing. The repository owner requested AI-assisted development as part of the assessment.

## Commit 1 — Requirements

**Instruction:** Start the first commit in the supplied repository after discussing product scope and backend sequencing.
**AI contribution:** Drafted the requirements document covering HR workflows, annual base salary semantics, currency conversion, history, import behavior, exclusions, and acceptance criteria.
**Checks performed:** Inspected the repository, confirmed its initial README, created the document, and read the committed file back from GitHub.
**Limits:** No application was implemented or tested. Modern Angular is proposed, but the assessment's AngularJS wording remains open.

## Commit 2 — Architecture and design

**Instruction:** “yaa do commit 2”, referring to the agreed backend architecture, data model, API conventions, and decision-record milestone.
**AI contribution:** Authored the architecture/module boundaries, logical schema, API contract, ADRs, documentation index, and this log.
**Design review and corrections:** Made reporting's cross-module SQL access explicit through published read views; required synchronous transactional audit writes; specified a shared employee version protocol for salary/profile changes; defined stale-preview revalidation and duplicate-apply behavior; avoided floating-point median calculations; documented that same-day salary corrections are excluded by the current requirements.
**Checks:** Compared the design against the committed requirements and reviewed document links, module dependencies, route/schema consistency, and planned-versus-implemented wording. Publication verification is performed when the commit is created.
**Limits:** No dependency compatibility build, database constraint execution, load test, or runtime security test has occurred. These documents describe intended behavior. Technology versions must be resolved against official documentation during bootstrap.

## Commit 3 — Backend bootstrap and CI

**Instruction:** “yaa do commit 3”, referring to the Spring Boot scaffold, Maven Wrapper, formatting, configuration, smoke test, and CI milestone.
**AI contribution:** Created the backend POM, application entry point, local/shared configuration, three real-HTTP smoke tests, official generated Maven Wrapper, checksum pin, repository hygiene files, SHA-pinned CI workflow, and build documentation. Resolved Spring Boot 4.1.2 with Java 25 against official documentation and Maven Central; verified current GitHub Action releases.
**Checks performed before publication:** Java 25 compilation; Maven Wrapper build with Spotless and Enforcer; three HTTP tests passing; executable JAR packaging; direct packaged-JAR health smoke; XML/YAML parsing and whitespace checks. CI is configured to repeat clean verify; its remote result is checked after publication rather than assumed here.
**Failures and corrections:** Initial Maven access needed workspace proxy settings and the existing host trust store, both kept outside the repository. The first HTTP test expected an exact health JSON string; Spring Boot included probe group names, so the test now verifies UP and absence of sensitive component/details fields structurally. Configured the Mockito Java agent explicitly instead of relying on dynamic attachment on Java 25. Spotless applied formatting before the final verification.
**Limits:** This milestone has no HR business endpoints, database, authentication, frontend, or deployment. Windows wrapper execution has not been tested in this Linux workspace. A Spotless dependency emits a Java Unsafe deprecation warning; formatting still passes, and no application code uses Unsafe.

## Commit 4 — Module boundaries and error contract

**Instruction:** “yaa do commit 4”, the module-verification and shared-error-contract milestone.
**AI contribution:** Added the Modulith BOM/API/test-core dependencies; seven business boundary declarations and a technical platform module; request correlation and safe Problem Details handling; positive/negative architecture checks, real-HTTP error tests, and MDC cleanup tests. Updated the implemented-versus-planned documentation.
**Checks:** Clean Java 25 verification passed all 18 tests, including intentionally invalid fixture rejection. Formatting and packaging pass. Reviewed documentation, module ownership, safe-message behavior, and absence of test fixtures in the executable artifact; remote CI is checked after publication.
**Failures and corrections:** The initial filter name collided with Spring's requestContextFilter bean; renamed it RequestCorrelationFilter without enabling bean overrides. Modulith excludes test classes by default, so isolated negative fixture scans explicitly include their test classpath while production scans keep the default. Maven Central exposed stable 2.1.2 beyond the reference site's 2.1.1 label; pinned and built 2.1.2. No business contracts or fake service implementations were invented to fill empty modules.
**Limits:** MVC error handling is not a substitute for future Spring Security exception handlers. Database integrity and business-rule tests remain for later commits. Logging only exception type and request ID intentionally limits diagnostic detail to avoid leaking exception contents.

## Commit 5 — PostgreSQL schema and migration

**Instruction:** Add PostgreSQL schema and Flyway migration as the next incremental commit.
**AI contribution:** Drafted V1 schema, Compose service, Boot database configuration, CI PostgreSQL service, and direct JDBC integration tests. Reviewed temporal exclusion semantics and changed the current compensation view to an explicit UTC date.
**Verification:** Java compile/package and formatting completed locally; full database integration runs in GitHub Actions with a real PostgreSQL 18.6 service. Local Docker and PostgreSQL are unavailable in this workspace, so no local database test result is claimed.
**Limit:** Spring Session tables, seed data, business use cases, and database permission separation are later milestones. Database constraints do not independently enforce all cross-table business rules.

## Commit 6 — Reproducible synthetic seed

**Instruction:** “yaa do commit 6”, the agreed 10,000-employee seed milestone.
**AI contribution:** Drafted an explicit, transaction-wrapped SQL seed, stable identifiers, multi-country salary histories, a disabled attribution actor, a fixed labeled reporting rate set, and PostgreSQL integration tests for counts, completeness, and rerun preservation.
**Checks:** Full verification against the CI PostgreSQL service is performed after publishing; local compile/format checks run before publication. The script is deliberately excluded from Flyway and requires an explicit demo command.
**Limits:** Names and salary scales are synthetic. Exchange rates are illustrative, not suitable for financial decisions. The script is for isolated demonstrations, never production data.

## Commit 7 — HR authentication

**Instruction:** “yaa do commit 7”, the planned authenticated HR session milestone.
**AI contribution:** Added Spring Security configuration, JDBC-backed sessions through Flyway V2, password-hash lookup, explicit local-only HR provisioning, login/me/logout/CSRF endpoints, and an HTTP integration test. Kept test-only probe routes isolated with a test-specific security chain.
**Checks:** Java 25 compilation and formatting passed locally. CI performs the PostgreSQL-backed HTTP and schema suite after publication. Security behavior is judged from that run and corrected if required.
**Limits:** This is one HR role and a first-party session flow. An enterprise identity provider and rate limiting are not part of the assessment release. Local provisioning never replaces a password for an existing email.

## Commit 8 — Employee directory and profile

**Instruction:** “Commit 8”, continuing the planned backend milestones.
**AI contribution:** Implemented authenticated employee list and profile endpoints with Spring `JdbcClient`, server-side search and filters, allowlisted sorting, bounded pagination, stable ordering, current-compensation projection, and decimal-string money responses. Added full-HTTP PostgreSQL tests covering authentication, filter/sort/page behavior, literal wildcard search, profile lookup, safe not-found behavior, and invalid parameters.
**Checks:** Java 25 formatting, compilation, test compilation, and executable packaging run locally. The full suite runs against a real PostgreSQL 18.6 service in CI after publication; any failure is corrected before the milestone is considered complete. The first CI run caught a two-character PostgreSQL `ESCAPE` literal and proxy-based parameter validation producing 500. The correction uses a one-character escape and Spring MVC's native method validation so bad parameters follow the shared 400 contract.
**Design choices:** A focused read repository is used instead of loading 10,000 employees into memory. Dynamic SQL is limited to an internal sort-column allowlist and validated direction; all user values remain bound parameters. Employee ID is a deterministic tie-breaker. No distributed cache or search cluster is introduced because indexed relational reads are adequate for the stated scale and simpler to operate correctly.
**Limits:** This milestone is read-only. Salary history and mutations, dedicated employee edits, reporting aggregates, CSV transfer, and performance/load evidence remain later work.

## Commit 9 — Transactional salary changes and audit history

**Instruction:** Implement `feat: implement transactional salary changes and audit history`, including decimal/effective-date rules, optimistic locking, history, synchronous audit events, and tests for success, rollback, overlaps, stale writes, and history.
**AI contribution:** Added narrow public module contracts for employee version claims, currency precision, and append-only audit writes; implemented salary history and a transactional salary-change use case; derived the actor from the authenticated principal and audit request ID from correlation context; preserved safe domain error codes; and added full-HTTP PostgreSQL integration tests with a fixed UTC clock.
**Checks:** Java 25 formatting, compilation, test compilation, and executable packaging run locally. GitHub Actions performs the authoritative full suite with PostgreSQL 18.6 after publication. Tests cover successful period closure/insertion/audit, newest-first pagination, currency precision, future and overlapping dates, missing employees, stale versions, and forced audit failure rollback.
**Design choices:** PostgreSQL row locking plus an employee-version compare-and-swap serializes cooperating writes. Salary and audit records remain in one local transaction because audit is mandatory; Kafka would weaken atomicity without an outbox and adds no value for this use case. Database temporal constraints remain defense in depth. A fixed injectable `Clock` keeps business-date tests deterministic.
**Limits:** Same-day corrections and editing old periods remain deliberately excluded. The API supports salary commands, not employee profile writes. Reporting, imports, audit browsing/retention policy, and performance evidence remain later milestones.

## Commit 10 — Currency-normalized compensation reports

**Instruction:** Add summary totals, mean, median, department/country/level breakdowns, explicit rate-set metadata, and tests proving calculations, filters, rounding, and missing-rate behavior.
**AI contribution:** Implemented authenticated summary and grouped compensation endpoints, deterministic rate-set resolution, reusable filters, completeness validation, exact numeric conversion/aggregation, rank-based medians, HALF_EVEN response rounding, and explicit rate-set metadata. Added a small known PostgreSQL dataset covering cross-currency totals, odd/even medians, filters, empty results, stable department keys, inactive exclusion, invalid grouping, and missing rates.
**Checks:** Java 25 formatting, compilation, test compilation, and executable packaging run locally. The full PostgreSQL 18.6 suite runs in GitHub Actions after publication and is corrected before the milestone is closed.
**Design choices:** Reporting runs in a repeatable-read read-only transaction. It refuses partial results when salary or FX data is absent. SQL `NUMERIC` values remain exact through conversion and middle-value median calculation; rounding occurs only at the API boundary according to the reporting currency.
**Limits:** Rates are curated reference data, not live market FX. Reports cover current active compensation, not historical organization structure or pay-equity causality. Performance evidence on the 10,000-row seed remains a later milestone.

## Commit 11 — CSV validation and import previews

**Instruction:** Add file limits, CSV parsing, row validation, duplicate detection, and persisted previews bound to the user/file; invalid files must return useful row errors without employee writes.
**AI contribution:** Added strict UTF-8 RFC 4180 parsing with Apache Commons CSV 1.14.1, exact byte/row limits, SHA-256 hashing, a frozen 12-column template, bulk employee/reference snapshots, normalized create/update previews, optimistic expected versions, within-file duplicate detection, safe sorted row errors, authenticated ownership, and 24-hour persistence. Parsing/hashing occurs before the repeatable-read validation transaction to avoid holding a database connection during file I/O.
**Checks:** Java 25 formatting, compilation, test compilation, and packaging run locally. Multipart PostgreSQL tests cover quoted commas, valid create/update previews, owner/hash/version persistence, duplicate/reference/email/precision errors, absence of employee writes, 10,001 rows, and a payload over 10 MiB. GitHub Actions performs the final full-suite gate after publication.
**Design choices:** Validation returns 200 for inspectable INVALID previews, while transport limits use 413/415. Invalid previews store errors and counts but no normalized employee rows. Bulk snapshots avoid N+1 queries across the 10,000-row bound. Separate apply semantics are intentionally not smuggled into validation.
**Limits:** This commit does not apply previews, export CSV, clean expired rows, or guarantee idempotent application; those remain explicit follow-up work. Email syntax validation is intentionally practical rather than attempting full RFC mailbox validation.

## Ongoing practice

For each implementation milestone, append the actual prompt/task, generated or revised areas, checks executed, failures found, and resulting corrections. Never mark AI-generated code correct solely because it compiles; verify the business invariants with focused tests.
