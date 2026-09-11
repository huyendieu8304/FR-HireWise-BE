-- Team decision (redesign of US-MGR-05, UC-41): EM-13's alert now goes to
-- the Job's Recruiter, not its Hiring Manager (see SlaBreachWorker) - the
-- template still greeted "{{Manager_Name}}" from when it was designed for
-- Hiring Manager. Renaming here rather than at V43 (this email had not been
-- sent to anyone yet - UC-41 had zero real Hiring Manager to alert until
-- this same redesign, see explain/11-*.md) means no already-sent email is
-- affected, only the template row.
--
-- {{Recruiter_Name}} was already an accepted placeholder (see
-- TemplateVariableValidator's allow-list), so no other change is needed.
-- Guarded on the placeholder still being present so this migration is
-- idempotent and never clobbers an HR Admin's own edit to this template
-- (UC-09/UC-10) made after this migration ran.
UPDATE email_templates
SET body_template = replace(body_template, '{{Manager_Name}}', '{{Recruiter_Name}}'),
    updated_at = now()
WHERE code = 'EM-13' AND body_template LIKE '%{{Manager_Name}}%';
