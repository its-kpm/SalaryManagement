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

## Ongoing practice

For each implementation milestone, append the actual prompt/task, generated or revised areas, checks executed, failures found, and resulting corrections. Never mark AI-generated code correct solely because it compiles; verify the business invariants with focused tests.
