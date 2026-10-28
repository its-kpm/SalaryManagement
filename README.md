# SalaryManagement

Salary management for ACME's HR team and 10,000 employees across countries.

**Status:** PostgreSQL schema and a reproducible 10,000-employee demo seed, verified module boundaries, and shared HTTP error handling implemented. The application starts locally and exposes a minimal health endpoint. Employee APIs, authentication, persistence, and the frontend are not implemented yet; this is not a deployed HR application.

## Design documents

- [Product requirements](docs/requirements.md)
- [Backend architecture](docs/architecture.md)
- [Data model and invariants](docs/data-model.md)
- [API conventions and planned endpoints](docs/api-conventions.md)
- [Architecture decisions](docs/decisions.md)
- [AI assistance log](docs/ai-usage.md)

## Implementation direction

Spring Boot backend, PostgreSQL, and an Angular frontend (modern Angular is proposed; the assessment's AngularJS wording remains an open UI clarification). A modular monolith keeps deployment simple while enforcing business boundaries.

Use compatible, supported stable dependencies. Exact versions, build commands, and deployment instructions will be recorded when implemented.

Next milestone: employee directory and profiles. Later commits add compensation, reporting, CSV transfer, observability, performance checks, and deployment.

## Run the backend

Prerequisite: JDK 25 with JAVA_HOME set. PostgreSQL 18 is required for this milestone; Docker Compose can run the local database. The wrapper downloads Maven and dependencies on first use.

```bash
cd backend
./mvnw --batch-mode --no-transfer-progress clean verify
./mvnw spring-boot:run
```

On Windows use `mvnw.cmd` in place of `./mvnw`. Set `POSTGRES_PASSWORD` and `DATABASE_PASSWORD` to the same local development password, then run `docker compose up -d postgres` from the repository root before these commands. The default local profile binds to `127.0.0.1:8080`; set `PORT` to use another port.

```bash
curl http://127.0.0.1:8080/actuator/health
```

Expected response includes `"status":"UP"`; Spring Boot may also list health probe groups. Component details are omitted. This checks application and database health after Flyway has migrated the schema. Other Actuator endpoints are not exposed. Do not expose the bootstrap as a production HR service before the authentication milestone.

To run the packaged application after verification:

```bash
java -jar target/salary-management-0.0.1-SNAPSHOT.jar
```

## Build checks

`./mvnw clean verify` enforces Java 25/Maven requirements, checks Java formatting, runs HTTP smoke tests on an ephemeral port, and packages an executable JAR. Use `./mvnw spotless:apply` to format changes. Reports are in `backend/target/surefire-reports/`.

GitHub Actions runs the same verification for pushes to main, pull requests, and manual dispatch. Actions are pinned to commit SHAs and the workflow has read-only repository permissions. [Dependency baseline and verification notes](docs/build-baseline.md).

## Module and API foundation

Spring Modulith declares seven business modules plus a technical `platform` module. Architecture tests verify the complete module set, dependency allowlists, encapsulation, and absence of cycles. Deliberately invalid test fixtures prove that the gate rejects each violation; they are not packaged in the application.

MVC errors return RFC 9457 Problem Details with safe messages and a server-generated `requestId` matching `X-Request-ID`. API responses use `Cache-Control: no-store`. Validation errors identify fields without returning rejected salary values. Business-specific errors and security-filter errors arrive with their respective features.

The suite currently has 18 tests: 4 architecture checks, 9 HTTP error/response checks, 2 request-context cleanup checks, and 3 original startup/health checks. Test-only HTTP endpoints are outside the application scan and are not shipped.

## Database schema

Flyway applies `backend/src/main/resources/db/migration/V1__initial_schema.sql` at startup. The schema includes users, reference data, employees, effective-dated salaries, rate sets, audits, and import previews. A PostgreSQL exclusion constraint prevents overlapping salary periods. Application startup fails if the database is unreachable or a migration is invalid.

Run locally from the repository root:

```bash
export POSTGRES_PASSWORD='choose-a-local-dev-password'
export DATABASE_PASSWORD="$POSTGRES_PASSWORD"
docker compose up -d postgres
cd backend && ./mvnw clean verify
```

CI starts PostgreSQL 18.6 as a service and runs the same full verification. Local database and credentials are development-only; no demo HR login is created automatically. Seed rows are added only when the script is explicitly run.

## Synthetic demonstration data

After Flyway has created the schema, run the SQL script from the repository root. It generates exactly 10,000 synthetic employees and 12,500 effective-dated salary periods across four countries, four currencies, five departments, and five levels. One in twenty employees is inactive; one in four has a historical salary change. The fixed USD reporting rate set is labeled as synthetic, dated 2026-01-01, and is not a live financial rate.

```bash
docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U salary_app -d salary_management < scripts/seed.sql
```

The script commits atomically and can be rerun. Deterministic identifiers and conflict handling preserve later edits instead of rewriting salaries. It creates a disabled seed actor solely to attribute the synthetic rows; authentication and usable HR accounts arrive in the next commit. Run this script only against a disposable demo database.

## Local HR login

Start the backend with an explicit local demo account after the database is migrated. Never commit a credential. The account is created only if its email does not already exist; restarting with another password will not reset it.

```bash
export DEMO_HR_EMAIL='hr@demo.invalid'
export DEMO_HR_PASSWORD='choose-a-unique-password-at-least-12-chars'
cd backend
./mvnw spring-boot:run -Dspring-boot.run.arguments=--demo.hr.enabled=true
```

The default local profile allows HTTP only on loopback and sets the session cookie Secure flag to false. Nonlocal profiles default to Secure cookies and require HTTPS. First GET `/api/v1/auth/csrf` to obtain a token and session cookie. Send its `headerName: token` on login and other POST requests. GET `/api/v1/auth/csrf` again after login to refresh the token. Use the session cookie on protected routes, GET `/api/v1/auth/me` to inspect the user, and POST `/api/v1/auth/logout` with a CSRF token to end the session. The synthetic seed actor remains disabled and cannot log in.
