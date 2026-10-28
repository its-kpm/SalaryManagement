# SalaryManagement

Salary management for ACME's HR team and 10,000 employees across countries.

**Status:** requirements and backend design committed; application implementation has not started. No deployed service or passing application tests are claimed yet.

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

Next milestone: bootstrap the backend, Maven Wrapper, and CI. Subsequent commits add module verification, migrations, seed data, authentication, employee workflows, compensation, reporting, CSV transfer, observability, performance checks, and deployment.
