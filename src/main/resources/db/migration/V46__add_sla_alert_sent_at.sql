-- US-MGR-05 (UC-41, SLA Monitoring): tracks whether the CURRENT stage-dwell
-- of an Application has already triggered an SLA breach alert email, so
-- SlaBreachWorker never re-sends one on every poll for a candidate still
-- sitting past their Stage's sla_hours. NULL = not yet alerted for this
-- dwell. Reset back to NULL every time the Application's current_stage_id
-- changes (see every call site that also updates last_stage_changed_at -
-- KanbanService, InterviewService, ApplicationRejectionService,
-- OfferService, OfferSigningService) so a fresh breach in the NEXT stage
-- is never silently suppressed by a flag left over from a previous one.
ALTER TABLE applications ADD COLUMN sla_alert_sent_at TIMESTAMPTZ NULL;
