# Architecture decision records

Status: accepted design direction for the assessment; implementation evidence will be added as code lands. Exact dependency versions remain pending bootstrap verification.

## ADR-001 — Modular monolith

**Context:** One organization, one HR persona, 10,000 employees, and a short assessment delivery cycle.
**Decision:** One Spring Boot deployable, business-oriented modules, Spring Modulith boundary tests, one PostgreSQL database.
**Alternatives:** Layer-only packaging is easy but permits uncontrolled coupling; microservices introduce network failures, distributed transactions, and operational overhead.
**Consequence:** Scale/deploy the backend together. Module APIs preserve an extraction path if independent operational needs emerge; extraction is not a current goal.

## ADR-002 — Relational transactions and effective-dated salaries

**Decision:** PostgreSQL, Flyway, decimal money, effective-date ranges, optimistic employee versions, and synchronous audit writes in the salary transaction. JPA supports writes; explicit SQL supports analytical reads.
**Alternatives:** SQLite simplifies setup but is less representative of intended concurrent deployment. A mutable salary column loses history; event sourcing adds replay/schema complexity.
**Consequence:** PostgreSQL-specific exclusion constraints and read views are intentional portability trade-offs. Same-day salary corrections and earlier-period edits remain excluded.

## ADR-003 — First-party session authentication

**Decision:** Spring Security with secure cookies, CSRF protection, adaptive password hashing, PostgreSQL-backed Spring Session, and one HR role. No public registration.
**Alternatives:** JWTs introduce token storage/revocation decisions unnecessary for this browser app; external OIDC is appropriate when enterprise identity is supplied.
**Consequence:** Sessions incur database access and expiry cleanup. Demo user credentials are provisioned through deployment configuration, never committed. Revisit OIDC for organizational SSO.

## ADR-004 — Explicit reporting semantics

**Decision:** Report annual gross base salary, normalize with immutable reference-rate sets, disclose conversion metadata, and compute aggregates using decimals. Use current employee attributes and salary periods.
**Alternatives:** Summing local amounts is invalid; live FX adds volatility and external failure; an AI chatbot adds ambiguity without replacing reliable metrics.
**Consequence:** Reports are reproducible for a fixed data snapshot and rate set, but do not represent full employer cost, purchasing power, or causal pay-equity conclusions.

## ADR-005 — Validate before atomic import

**Decision:** Bound CSV size/rows, persist user-owned previews, revalidate at apply, apply once per preview in one transaction, and purge staged payloads after success/expiry.
**Alternatives:** Partial row success makes reconciliation harder; background chunking changes atomicity and restart semantics.
**Consequence:** Large imports hold locks and consume transaction resources. Measure the supported 10,000-row case; reduce limits or explicitly revise UX/atomicity if operational budgets cannot be met. Do not silently switch to partial success.

## ADR-006 — Defer infrastructure until justified

**Decision:** No required Redis, Kafka, Elasticsearch, Kubernetes, reactive stack, or salary-report cache. Use database queries, metrics, and measured tuning first.
**Revisit triggers:** Redis for demonstrated shared-cache/session needs; Kafka plus transactional outbox for durable integration with independent payroll/finance consumers; Caffeine for measured repeated reference reads; background workers for proven long-running workflows.
**Consequence:** Fewer failure modes and simpler deployment. No claim of event-driven delivery or cache performance until implemented and tested. Optional virtual threads require compatibility/load checks; they do not increase database connection capacity.

## ADR-007 — Stable compatible dependencies

**Decision:** Prefer supported stable technology, resolve versions from official compatibility guidance at bootstrap, use framework BOMs and wrappers, and pin versions/images. Evaluate Java 25 and the Spring Boot 4.1 family as the proposed baseline.
**Consequence:** Latest does not mean independently upgrading every transitive library or using previews. Any deployment/toolchain constraint is recorded before changing the baseline. Frontend AngularJS versus modern Angular remains explicitly unresolved; it does not block backend work.
