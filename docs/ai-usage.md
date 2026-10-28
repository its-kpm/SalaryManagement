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

## Ongoing practice

For each implementation milestone, append the actual prompt/task, generated or revised areas, checks executed, failures found, and resulting corrections. Never mark AI-generated code correct solely because it compiles; verify the business invariants with focused tests.
