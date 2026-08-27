-- Seeds a small starter set of departments, including one nested
-- (parent/child) pair to exercise the department hierarchy (BR-RBAC-06).
-- Each INSERT is guarded with a NOT EXISTS check on the name so this
-- migration stays safe to re-run against a database that already has
-- these rows (e.g. a reset dev/test environment).

INSERT INTO departments (name, parent_department_id, is_active)
SELECT 'Engineering', NULL, true
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE name = 'Engineering');

INSERT INTO departments (name, parent_department_id, is_active)
SELECT 'Human Resources', NULL, true
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE name = 'Human Resources');

INSERT INTO departments (name, parent_department_id, is_active)
SELECT 'Sales & Marketing', NULL, true
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE name = 'Sales & Marketing');

INSERT INTO departments (name, parent_department_id, is_active)
SELECT 'Backend Team', (SELECT department_id FROM departments WHERE name = 'Engineering'), true
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE name = 'Backend Team');

INSERT INTO departments (name, parent_department_id, is_active)
SELECT 'Frontend Team', (SELECT department_id FROM departments WHERE name = 'Engineering'), true
WHERE NOT EXISTS (SELECT 1 FROM departments WHERE name = 'Frontend Team');
