# SalaryManagement

Salary management for ACME's HR team and 10,000 employees across countries.

**Status:** backend bootstrap, verified module boundaries, and shared HTTP error handling implemented. The application starts locally and exposes a minimal health endpoint. Employee APIs, authentication, persistence, and the frontend are not implemented yet; this is not a deployed HR application.

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

Next milestone: PostgreSQL schema and Flyway migrations. Later commits add seed data, authentication, employee workflows, compensation, reporting, CSV transfer, observability, performance checks, and deployment.

## Run the backend

Prerequisite: JDK 25 with JAVA_HOME set. No separate Maven or database installation is needed for this milestone. The wrapper downloads Maven and dependencies on first use.

```bash
cd backend
./mvnw --batch-mode --no-transfer-progress clean verify
./mvnw spring-boot:run
```

On Windows use `mvnw.cmd` in place of `./mvnw`. The default local profile binds to `127.0.0.1:8080`; set `PORT` to use another port.

```bash
curl http://127.0.0.1:8080/actuator/health
```

Expected response includes `"status":"UP"`; Spring Boot may also list health probe groups. Component details are omitted. This checks application health only; no database exists yet. Other Actuator endpoints are not exposed. Do not expose the bootstrap as a production HR service before the authentication milestone.

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
