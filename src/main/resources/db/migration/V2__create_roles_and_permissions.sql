--  Ma role/permission va bang phan quyen
-- KHONG hard-code permission theo role rai rac trong Java.

CREATE TABLE IF NOT EXISTS roles (
    role_id     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(50) NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    is_system   BOOLEAN NOT NULL DEFAULT true
);

CREATE TABLE IF NOT EXISTS permissions (
    permission_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code            VARCHAR(50) NOT NULL UNIQUE,
    description     VARCHAR(255),
    -- Phan biet hanh dong doc (chi can can_write=false tren access scope) vs
    -- hanh dong ghi (yeu cau can_write=true) - xem AccessScopeService.
    is_write        BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE IF NOT EXISTS role_permissions (
    role_id         BIGINT NOT NULL REFERENCES roles (role_id) ON DELETE CASCADE,
    permission_id   BIGINT NOT NULL REFERENCES permissions (permission_id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- 5 role he thong
INSERT INTO roles (code, name, is_system) VALUES
    ('HR_ADMIN', 'HR Admin', true),
    ('RECRUITER', 'Recruiter', true),
    ('HIRING_MANAGER', 'Hiring Manager', true),
    ('INTERVIEWER', 'Interviewer', true),
    ('CANDIDATE', 'Candidate (Portal)', true)
ON CONFLICT (code) DO NOTHING;

-- 28 permissions
INSERT INTO permissions (code, description, is_write) VALUES
    ('USER_CREATE', 'Create a new internal user account', true),
    ('USER_UPDATE', 'Update information / block or unblock an internal user account', true),
    ('USER_VIEW', 'View the list and details of internal user accounts', false),
    ('ROLE_ASSIGN', 'Assign roles and department/Job scope to user accounts', true),
    ('PIPELINE_MANAGE', 'Create, update, delete, and reorder stages and pipeline templates', true),
    ('EMAIL_TEMPLATE_MANAGE', 'Manage email templates', true),
    ('INTEGRATION_MANAGE', 'Connect, disconnect, and renew OAuth integrations for Cloud Storage, Calendar API, and Social API', true),
    ('JOB_CREATE', 'Create a new Job Position in Draft status', true),
    ('JOB_EDIT', 'Edit a Job Position in Draft or Rejected status', true),
    ('JOB_SUBMIT', 'Submit a Job Position for approval', true),
    ('JOB_APPROVE', 'Approve or reject a Job Position', true),
    ('JOB_VIEW', 'View internal Job Position details', false),
    ('JOB_PUBLISH', 'Publish an approved Job Position to public and multi-channel platforms', true),
    ('APPLICATION_VIEW', 'View applicant cards and application profiles', false),
    ('APPLICATION_MOVE_STAGE', 'Move an application to a different stage on the Kanban board', true),
    ('APPLICATION_REJECT', 'Reject an application', true),
    ('AI_VIEW', 'View Match Scores and AI Highlights', false),
    ('INTERVIEW_SCHEDULE', 'Schedule interviews and send self-service booking invitations', true),
    ('INTERVIEW_BOOK', 'Select an interview time slot through self-service booking', true),
    ('SCORECARD_TEMPLATE_MANAGE', 'Configure Scorecard criteria and weights', true),
    ('SCORECARD_SUBMIT', 'Submit Scorecard ratings and feedback', true),
    ('OFFER_CREATE', 'Create an Offer Letter from a template', true),
    ('OFFER_SEND', 'Send an Offer and request an electronic signature', true),
    ('OFFER_SIGN', 'Electronically sign an Offer', true),
    ('SLA_CONFIGURE', 'Configure SLA thresholds by stage', true),
    ('SLA_VIEW_ALERT', 'View SLA violation alerts', false),
    ('REPORT_VIEW', 'View reporting dashboards', false),
    ('CANDIDATE_APPLY', 'Submit an application through the public Job Board', true),
    ('CANDIDATE_PROFILE_VIEW', 'View the status of your own application', false)
    ON CONFLICT (code) DO NOTHING;

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM roles r JOIN permissions p ON (
    (r.code = 'HR_ADMIN' AND p.code IN (
        'USER_CREATE','USER_UPDATE','USER_VIEW','ROLE_ASSIGN','PIPELINE_MANAGE',
        'EMAIL_TEMPLATE_MANAGE','INTEGRATION_MANAGE','JOB_VIEW',
        'SCORECARD_TEMPLATE_MANAGE','SLA_CONFIGURE'
    ))
    OR (r.code = 'RECRUITER' AND p.code IN (
        'JOB_CREATE','JOB_EDIT','JOB_SUBMIT','JOB_VIEW','JOB_PUBLISH',
        'APPLICATION_VIEW','APPLICATION_MOVE_STAGE','APPLICATION_REJECT','AI_VIEW',
        'INTERVIEW_SCHEDULE','OFFER_CREATE','OFFER_SEND','SLA_VIEW_ALERT','REPORT_VIEW'
    ))
    OR (r.code = 'HIRING_MANAGER' AND p.code IN (
        'JOB_APPROVE','JOB_VIEW','APPLICATION_VIEW','AI_VIEW',
        'SCORECARD_TEMPLATE_MANAGE','SCORECARD_SUBMIT','SLA_CONFIGURE',
        'SLA_VIEW_ALERT','REPORT_VIEW'
    ))
    OR (r.code = 'INTERVIEWER' AND p.code IN ('APPLICATION_VIEW','SCORECARD_SUBMIT'))
    OR (r.code = 'CANDIDATE' AND p.code IN (
        'INTERVIEW_BOOK','OFFER_SIGN','CANDIDATE_APPLY','CANDIDATE_PROFILE_VIEW'
    ))
)
ON CONFLICT DO NOTHING;
