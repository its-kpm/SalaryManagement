-- Synthetic demo data only. Run against an isolated database after Flyway V1.
-- One transaction; fixed IDs and dates allow safe reruns without overwriting HR edits.
BEGIN;

INSERT INTO hr_users(id, email, password_hash, role, enabled)
VALUES (md5('acme-demo-seed-actor')::uuid, 'seed-actor@demo.invalid', 'DISABLED-SEED-ACTOR', 'HR', false)
ON CONFLICT (id) DO NOTHING;

INSERT INTO countries(code, name) VALUES
  ('US', 'United States'), ('IN', 'India'), ('GB', 'United Kingdom'), ('DE', 'Germany')
ON CONFLICT (code) DO NOTHING;
INSERT INTO currencies(code, minor_units) VALUES
  ('USD', 2), ('INR', 2), ('GBP', 2), ('EUR', 2)
ON CONFLICT (code) DO NOTHING;
INSERT INTO departments(id, name)
SELECT md5('acme-demo-department:' || name)::uuid, name
FROM (VALUES ('Engineering'), ('Product'), ('Sales'), ('Operations'), ('Finance')) AS d(name)
ON CONFLICT (id) DO NOTHING;

INSERT INTO exchange_rate_sets(id, reporting_currency, as_of_date, label)
VALUES (md5('acme-demo-rate-set:2026-01-01:USD')::uuid,
        'USD', DATE '2026-01-01', 'Synthetic reference rates for demo reporting')
ON CONFLICT (id) DO NOTHING;
INSERT INTO exchange_rates(rate_set_id, source_currency, rate) VALUES
  (md5('acme-demo-rate-set:2026-01-01:USD')::uuid, 'USD', 1.000000000000),
  (md5('acme-demo-rate-set:2026-01-01:USD')::uuid, 'INR', 0.012000000000),
  (md5('acme-demo-rate-set:2026-01-01:USD')::uuid, 'GBP', 1.300000000000),
  (md5('acme-demo-rate-set:2026-01-01:USD')::uuid, 'EUR', 1.100000000000)
ON CONFLICT (rate_set_id, source_currency) DO NOTHING;

CREATE TEMP TABLE demo_seed_rows ON COMMIT DROP AS
SELECT n,
       md5('acme-demo-employee:' || n)::uuid AS employee_id,
       'ACME-' || lpad(n::text, 5, '0') AS employee_number,
       ('employee-' || n || '@demo.invalid') AS email,
       (ARRAY['Avery','Jordan','Morgan','Riley','Casey','Taylor','Sam','Alex'])[(n % 8) + 1]
         || ' ' || (ARRAY['Rivera','Patel','Chen','Wilson','Khan','Miller','Singh','Brown'])[((n / 8) % 8) + 1] AS full_name,
       (ARRAY['Engineering','Product','Sales','Operations','Finance'])[(n % 5) + 1] AS department_name,
       (ARRAY['US','IN','GB','DE'])[(n % 4) + 1] AS country_code,
       (ARRAY['L1','L2','L3','L4','L5'])[(n % 5) + 1] AS job_level,
       (n % 20 <> 0) AS is_active,
       (n % 4 = 0) AS has_history
FROM generate_series(1, 10000) AS n;

INSERT INTO employees(id, employee_number, email, full_name, department_id,
                      country_code, job_title, job_level, employment_status)
SELECT employee_id, employee_number, email,
       full_name, md5('acme-demo-department:' || department_name)::uuid,
       country_code, 'Specialist ' || job_level, job_level,
       CASE WHEN is_active THEN 'ACTIVE' ELSE 'INACTIVE' END
FROM demo_seed_rows
ON CONFLICT (id) DO NOTHING;

-- An initial salary for all employees; every fourth employee has a later review.
INSERT INTO salary_records(id, employee_id, annual_base_amount, currency_code,
                           effective_from, effective_to, reason, actor_id)
SELECT md5('acme-demo-salary:initial:' || n)::uuid, employee_id,
       (CASE country_code
           WHEN 'US' THEN 45000
           WHEN 'IN' THEN 700000
           WHEN 'GB' THEN 36000
           ELSE 40000
        END + (n % 5) *
        CASE country_code WHEN 'IN' THEN 250000 ELSE 11000 END +
        (n % 19) * CASE country_code WHEN 'IN' THEN 1500 ELSE 100 END)::numeric(19,4),
       CASE country_code WHEN 'US' THEN 'USD' WHEN 'IN' THEN 'INR'
            WHEN 'GB' THEN 'GBP' ELSE 'EUR' END,
       DATE '2022-01-01',
       CASE WHEN has_history THEN DATE '2025-01-01' ELSE NULL END,
       'Initial synthetic salary', md5('acme-demo-seed-actor')::uuid
FROM demo_seed_rows
ON CONFLICT (id) DO NOTHING;

INSERT INTO salary_records(id, employee_id, annual_base_amount, currency_code,
                           effective_from, effective_to, reason, actor_id)
SELECT md5('acme-demo-salary:review:' || n)::uuid, employee_id,
       (CASE country_code
           WHEN 'US' THEN 50000 WHEN 'IN' THEN 800000
           WHEN 'GB' THEN 40000 ELSE 44000
        END + (n % 5) *
        CASE country_code WHEN 'IN' THEN 280000 ELSE 12000 END +
        (n % 19) * CASE country_code WHEN 'IN' THEN 1800 ELSE 120 END)::numeric(19,4),
       CASE country_code WHEN 'US' THEN 'USD' WHEN 'IN' THEN 'INR'
            WHEN 'GB' THEN 'GBP' ELSE 'EUR' END,
       DATE '2025-01-01', NULL,
       'Synthetic compensation review', md5('acme-demo-seed-actor')::uuid
FROM demo_seed_rows WHERE has_history
ON CONFLICT (id) DO NOTHING;
COMMIT;
