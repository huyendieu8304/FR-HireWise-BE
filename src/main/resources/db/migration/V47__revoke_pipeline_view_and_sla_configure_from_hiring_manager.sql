-- Team decision (redesign of US-MGR-04, UC-40): SLA is configured by HR
-- Admin alongside the rest of a Stage's structure (PIPELINE_MANAGE),
-- not by Hiring Manager through a separate permission - SLA_CONFIGURE
-- endpoint (PipelineService#updateStageSla) no longer checks SLA_CONFIGURE,
-- it now checks PIPELINE_MANAGE like every other Stage mutation.
--
-- HIRING_MANAGER no longer needs either grant it got at V45/V2:
-- - PIPELINE_VIEW (V45) was only ever added so Hiring Manager could see
--   Stages to configure their SLA on the Pipeline Management page - moot now.
-- - SLA_CONFIGURE (V2, original seed) is no longer read by any endpoint.
--
-- Both permissions themselves stay defined (HR_ADMIN keeps SLA_CONFIGURE
-- from V2, unused but harmless; RECRUITER keeps PIPELINE_VIEW from V42,
-- unaffected) - only the HIRING_MANAGER grants are revoked.
DELETE FROM role_permissions
WHERE role_id = (SELECT role_id FROM roles WHERE code = 'HIRING_MANAGER')
  AND permission_id IN (
      SELECT permission_id FROM permissions WHERE code IN ('PIPELINE_VIEW', 'SLA_CONFIGURE')
  );
