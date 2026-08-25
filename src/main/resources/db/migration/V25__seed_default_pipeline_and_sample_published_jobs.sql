-- Seeds a default company-wide Pipeline Template (UC-04) and a handful of
-- already-Published job positions, so UC-16 (public Job Board) and UC-17
-- (apply) are exercisable end-to-end without first needing UC-12/13/14/15
-- (draft/submit/approve a job) to be implemented and run by hand.
--
-- Guarded with NOT EXISTS checks on natural keys (name/title), same
-- idempotent-reseed pattern as V12__seed_departments.sql.

-- job_positions.id/candidates.id/applications.id are plain UUID columns
-- with no DB-side default (see domain.JobPosition etc - the app assigns
-- them via UUID.randomUUID()), so seed rows need explicit literals.

INSERT INTO pipeline_templates (name, department_id, status, created_at, updated_at)
SELECT 'Default Hiring Pipeline', NULL, 'ACTIVE', now(), now()
WHERE NOT EXISTS (SELECT 1 FROM pipeline_templates WHERE name = 'Default Hiring Pipeline');

INSERT INTO pipeline_stages (pipeline_template_id, name, code, stage_type, position, is_terminal, is_active, created_at, updated_at)
SELECT pt.pipeline_template_id, v.name, v.code, v.stage_type, v.position, v.is_terminal, true, now(), now()
FROM pipeline_templates pt
CROSS JOIN (VALUES
    ('New',       'NEW',       'INTAKE',            1, false),
    ('Screening', 'SCREENING', 'SCREENING',         2, false),
    ('Interview', 'INTERVIEW', 'INTERVIEW',         3, false),
    ('Offer',     'OFFER',     'OFFER',             4, false),
    ('Hired',     'HIRED',     'TERMINAL_SUCCESS',  5, true),
    ('Rejected',  'REJECTED',  'TERMINAL_REJECTED', 6, true)
) AS v(name, code, stage_type, position, is_terminal)
WHERE pt.name = 'Default Hiring Pipeline'
  AND NOT EXISTS (
    SELECT 1 FROM pipeline_stages ps
    WHERE ps.pipeline_template_id = pt.pipeline_template_id AND ps.code = v.code
  );

INSERT INTO job_positions (id, title, description, requirements, benefits, department_id, location,
                            employment_type, salary_min, salary_max, openings, status, pipeline_template_id,
                            created_at, updated_at)
SELECT '11111111-1111-4111-8111-111111111111'::uuid,
       'Senior Backend Engineer',
       'Thiet ke va phat trien he thong backend cho nen tang ATS, xu ly luu luong lon voi kien truc microservices. Lam viec truc tiep voi team Product de hien thuc hoa cac tinh nang AI matching, pipeline tuyen dung va tich hop ben thu ba.',
       E'5+ nam kinh nghiem Java/Spring Boot hoac tuong duong\n- Kinh nghiem thiet ke he thong phan tan, microservices\n- Hieu biet ve message queue (Kafka/RabbitMQ) la loi the',
       E'Luong thang 13, thuong theo KPI\n- Bao hiem suc khoe cao cap cho nhan vien va gia dinh\n- Che do lam viec hybrid linh hoat',
       (SELECT department_id FROM departments WHERE name = 'Backend Team'),
       'Ho Chi Minh', 'FULL_TIME', 25000000, 35000000, 2, 'PUBLISHED',
       (SELECT pipeline_template_id FROM pipeline_templates WHERE name = 'Default Hiring Pipeline'),
       now(), now()
WHERE NOT EXISTS (SELECT 1 FROM job_positions WHERE title = 'Senior Backend Engineer');

INSERT INTO job_positions (id, title, description, requirements, benefits, department_id, location,
                            employment_type, salary_min, salary_max, openings, status, pipeline_template_id,
                            created_at, updated_at)
SELECT '22222222-2222-4222-8222-222222222222'::uuid,
       'Product Designer',
       'Thiet ke trai nghiem va giao dien cho cac san pham tuyen dung, phoi hop chat che voi Product va Engineering tu y tuong den ban giao.',
       E'3+ nam kinh nghiem Product/UX Design\n- Thanh thao Figma va he thong thiet ke (design system)\n- Co portfolio the hien tu duy giai quyet van de',
       E'Thoa thuan theo nang luc\n- Duoc tham gia dinh hinh san pham tu giai doan dau\n- Ngan sach hoc tap/cong cu thiet ke rieng',
       (SELECT department_id FROM departments WHERE name = 'Frontend Team'),
       'Ha Noi', 'FULL_TIME', NULL, NULL, 1, 'PUBLISHED',
       (SELECT pipeline_template_id FROM pipeline_templates WHERE name = 'Default Hiring Pipeline'),
       now(), now()
WHERE NOT EXISTS (SELECT 1 FROM job_positions WHERE title = 'Product Designer');

INSERT INTO job_positions (id, title, description, requirements, benefits, department_id, location,
                            employment_type, salary_min, salary_max, openings, status, pipeline_template_id,
                            created_at, updated_at)
SELECT '33333333-3333-4333-8333-333333333333'::uuid,
       'HR Business Partner',
       'Dong hanh cung cac truong phong ban ve chien luoc nhan su, tuyen dung va phat trien doi ngu.',
       E'3+ nam kinh nghiem HRBP hoac Tuyen dung\n- Ky nang giao tiep, tu van tot\n- Am hieu luat lao dong Viet Nam',
       E'Luong thang 13, thuong theo KPI\n- Bao hiem suc khoe cao cap cho nhan vien va gia dinh\n- Che do lam viec hybrid linh hoat',
       (SELECT department_id FROM departments WHERE name = 'Human Resources'),
       'Ho Chi Minh', 'FULL_TIME', 18000000, 25000000, 1, 'PUBLISHED',
       (SELECT pipeline_template_id FROM pipeline_templates WHERE name = 'Default Hiring Pipeline'),
       now(), now()
WHERE NOT EXISTS (SELECT 1 FROM job_positions WHERE title = 'HR Business Partner');

INSERT INTO job_positions (id, title, description, requirements, benefits, department_id, location,
                            employment_type, salary_min, salary_max, openings, status, pipeline_template_id,
                            created_at, updated_at)
SELECT '44444444-4444-4444-8444-444444444444'::uuid,
       'Data Analyst Intern',
       'Ho tro phan tich du lieu tuyen dung, xay dung bao cao va dashboard phuc vu ra quyet dinh.',
       E'Dang hoc nam cuoi/moi tot nghiep nganh CNTT, Toan, Thong ke...\n- Biet SQL co ban; biet Python/R la loi the\n- Ham hoc hoi, can than, chu dong',
       E'Duoc dao tao truc tiep tu Data team\n- Co hoi chuyen doi nhan vien chinh thuc\n- Lam viec Remote linh hoat',
       (SELECT department_id FROM departments WHERE name = 'Sales & Marketing'),
       'Remote', 'INTERNSHIP', 5000000, 7000000, 2, 'PUBLISHED',
       (SELECT pipeline_template_id FROM pipeline_templates WHERE name = 'Default Hiring Pipeline'),
       now(), now()
WHERE NOT EXISTS (SELECT 1 FROM job_positions WHERE title = 'Data Analyst Intern');
