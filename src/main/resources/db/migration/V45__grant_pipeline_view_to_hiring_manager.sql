-- US-MGR-04 (UC-40, SLA Monitoring): Hiring Manager configures the SLA
-- threshold (sla_hours) of each Interview-bearing Stage. SLA_CONFIGURE
-- (seeded V2, already granted to HIRING_MANAGER) gates the actual write
-- (PipelineService#updateStageSla), but the Pipeline Management screen also
-- needs to LIST the templates/stages first - gated by PIPELINE_VIEW (V42),
-- which HIRING_MANAGER never had (only HR_ADMIN/RECRUITER did). Without this
-- grant a Hiring Manager could never even see which Stage to configure.
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM roles r JOIN permissions p ON (
    p.code = 'PIPELINE_VIEW' AND r.code = 'HIRING_MANAGER'
)
ON CONFLICT DO NOTHING;
