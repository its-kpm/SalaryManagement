CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE hr_users (
    id UUID PRIMARY KEY,
    email TEXT NOT NULL UNIQUE CHECK (email = lower(btrim(email)) AND email <> ''),
    password_hash TEXT NOT NULL CHECK (btrim(password_hash) <> ''),
    role TEXT NOT NULL CHECK (role = 'HR'),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE departments (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL UNIQUE CHECK (btrim(name) <> '')
);
CREATE TABLE countries (
    code VARCHAR(2) PRIMARY KEY CHECK (code ~ '^[A-Z]{2}$'),
    name TEXT NOT NULL CHECK (btrim(name) <> '')
);
CREATE TABLE currencies (
    code VARCHAR(3) PRIMARY KEY CHECK (code ~ '^[A-Z]{3}$'),
    minor_units SMALLINT NOT NULL CHECK (minor_units BETWEEN 0 AND 3)
);
CREATE TABLE employees (
    id UUID PRIMARY KEY,
    employee_number TEXT NOT NULL UNIQUE CHECK (btrim(employee_number) <> ''),
    email TEXT NOT NULL UNIQUE CHECK (email = lower(btrim(email)) AND email <> ''),
    full_name TEXT NOT NULL CHECK (btrim(full_name) <> ''),
    department_id UUID NOT NULL REFERENCES departments(id),
    country_code VARCHAR(2) NOT NULL REFERENCES countries(code),
    job_title TEXT NOT NULL CHECK (btrim(job_title) <> ''),
    job_level TEXT NOT NULL CHECK (btrim(job_level) <> ''),
    employment_status TEXT NOT NULL CHECK (employment_status IN ('ACTIVE', 'INACTIVE')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX employees_filter_idx ON employees (employment_status, country_code, department_id, id);

CREATE TABLE salary_records (
    id UUID PRIMARY KEY,
    employee_id UUID NOT NULL REFERENCES employees(id),
    annual_base_amount NUMERIC(19,4) NOT NULL CHECK (annual_base_amount > 0),
    currency_code VARCHAR(3) NOT NULL REFERENCES currencies(code),
    effective_from DATE NOT NULL,
    effective_to DATE,
    reason TEXT NOT NULL CHECK (btrim(reason) <> ''),
    actor_id UUID NOT NULL REFERENCES hr_users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT salary_date_order CHECK (effective_to IS NULL OR effective_to > effective_from),
    CONSTRAINT salary_unique_start UNIQUE (employee_id, effective_from),
    CONSTRAINT salary_no_overlap EXCLUDE USING gist (
        employee_id WITH =,
        daterange(effective_from, effective_to, '[)') WITH &&
    )
);
CREATE UNIQUE INDEX salary_one_open_period ON salary_records(employee_id) WHERE effective_to IS NULL;
CREATE INDEX salary_employee_history_idx ON salary_records(employee_id, effective_from DESC);

CREATE TABLE exchange_rate_sets (
    id UUID PRIMARY KEY,
    reporting_currency VARCHAR(3) NOT NULL REFERENCES currencies(code),
    as_of_date DATE NOT NULL,
    label TEXT NOT NULL CHECK (btrim(label) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (reporting_currency, as_of_date)
);
CREATE TABLE exchange_rates (
    rate_set_id UUID NOT NULL REFERENCES exchange_rate_sets(id),
    source_currency VARCHAR(3) NOT NULL REFERENCES currencies(code),
    rate NUMERIC(24,12) NOT NULL CHECK (rate > 0),
    PRIMARY KEY (rate_set_id, source_currency)
);

CREATE TABLE imports (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES hr_users(id),
    content_hash VARCHAR(64) NOT NULL CHECK (content_hash ~ '^[0-9a-f]{64}$'),
    normalized_preview JSONB,
    expected_versions JSONB,
    status TEXT NOT NULL CHECK (status IN ('VALIDATED', 'INVALID', 'APPLIED')),
    errors JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    applied_at TIMESTAMPTZ,
    receipt JSONB,
    CHECK (expires_at > created_at),
    CHECK (status <> 'APPLIED' OR (applied_at IS NOT NULL AND receipt IS NOT NULL))
);
CREATE INDEX imports_owner_created_idx ON imports(owner_id, created_at DESC);
CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL REFERENCES hr_users(id),
    entity_type TEXT NOT NULL CHECK (btrim(entity_type) <> ''),
    entity_id UUID NOT NULL,
    action TEXT NOT NULL CHECK (btrim(action) <> ''),
    before_values JSONB,
    after_values JSONB,
    reason TEXT NOT NULL CHECK (btrim(reason) <> ''),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    request_id UUID,
    import_id UUID REFERENCES imports(id)
);
CREATE INDEX audit_entity_history_idx ON audit_events(entity_type, entity_id, occurred_at DESC);

CREATE VIEW current_employee_compensation AS
SELECT e.id AS employee_id, e.employee_number, e.full_name, e.email, e.department_id,
       e.country_code, e.job_level, e.employment_status, e.version,
       s.id AS salary_record_id, s.annual_base_amount, s.currency_code,
       s.effective_from, s.effective_to
FROM employees e LEFT JOIN salary_records s ON s.employee_id = e.id
    AND s.effective_from <= (now() AT TIME ZONE 'UTC')::date
    AND (s.effective_to IS NULL OR s.effective_to > (now() AT TIME ZONE 'UTC')::date);
