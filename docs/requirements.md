# Salary Management — Product Requirements

**ACME | Initial release | 28 October 2026**

## Goal and user
Replace spreadsheets with a secure web application that enables ACME's HR manager to maintain salary data for 10,000 employees across countries and answer common compensation questions. Success means trustworthy records and useful comparisons, not payroll processing.

## Scope and core workflows
- **Access:** An authenticated HR manager has organization-wide access; unauthenticated users cannot access employee data, reports, imports, or exports.
- **Directory:** Search by name, employee number, or email; filter by department, country, job level, and employment status; sort and paginate results; open employee profiles.
- **Compensation:** View current salary and history; record a salary change with amount, currency, effective date, and mandatory reason. Preserve prior values and record who changed what and when. Reject stale concurrent edits.
- **Reporting:** Show active employee count, annual base salary cost, average, median, and breakdowns by department, country, and job level. Consistent filters and employee drill-downs answer “What do we pay?”, “Where is spending concentrated?”, and “How does pay vary by level?”
- **Spreadsheet transition:** Import a documented CSV format to create employee records or update supported fields and compensation. Preview changes and row errors before confirmation; reject invalid batches without partial writes, revalidate before applying, and prevent duplicate application. Export filtered records safely.

## Compensation rules and assumptions
One organization, one HR role, and one current employment record per employee. Salary means **annual gross base salary**, excluding bonuses, equity, benefits, and employer taxes. Store monetary values with decimal precision and retain original currencies; require positive amounts and supported currency codes.

Salary periods must not overlap. Initial changes append a new period with an effective date strictly after the latest period's start and no later than today (UTC business date); editing earlier periods and future scheduling are excluded. Salary updates and audit records succeed or roll back together.

Reports use current salaries of active employees. Cross-currency comparisons use a fixed, versioned reference-rate set and disclose reporting currency and rate date. Missing rates produce an explicit incomplete-report error, never silently omitted salaries. Comparisons are descriptive, not claims of pay equity. All demo records are synthetic.

## Delivery and quality
Deliver a deployed UI and Spring Boot backend with a relational database, reproducible seed script for 10,000 employees, setup instructions, API documentation, and video demonstration. Frontend intent is modern Angular; the assessment's “AngularJS” wording needs clarification before UI implementation. Use supported stable, compatible frameworks; record exact versions and architectural trade-offs separately.

Use meaningful, fast, deterministic unit tests plus focused database and end-to-end tests. Protect sensitive data, exclude salary values from operational logs, and keep credentials out of source control. Maintain incremental Git commits and document actual AI assistance, validation, and corrections.

## Deliberately excluded
- **Payroll, taxes, bonuses, equity, benefits:** country-specific rules exceed base-salary management.
- **Employee self-service, approvals, multi-tenancy:** only one organization's HR persona is required.
- **Historical corrections and scheduled changes:** defer additional temporal workflows.
- **Live FX and AI chatbot:** reference rates and structured reports answer initial questions reproducibly.
- **Microservices and mandatory Kafka/Redis:** add infrastructure only when measured needs or integrations justify it.

## Acceptance criteria
1. A clean setup seeds exactly 10,000 employees reproducibly; rerunning creates no duplicates.
2. HR can search, filter, view, and change compensation; history and audit reflect the change, while invalid or conflicting updates leave data intact.
3. Known test datasets prove report totals, mean, median, filters, and currency handling.
4. Invalid CSV batches make no changes; valid confirmed batches apply once and exports respect filters.
5. Unauthorized access is denied; core tests pass in CI; measured performance on the seeded dataset and deployment/demo instructions are documented.
