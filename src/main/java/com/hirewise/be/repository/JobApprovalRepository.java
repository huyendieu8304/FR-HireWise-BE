package com.hirewise.be.repository;

import com.hirewise.be.domain.JobApproval;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for {@link JobApproval} entities.
 * <p>
 * UC-15: each Approve/Reject decision is persisted as a new append-style row
 * (rather than overwriting a single status column on job_positions) so every
 * past decision in a resubmit-after-rejection cycle is preserved for audit.
 * The actual job status is updated on {@code job_positions}; this table only
 * holds the decision trail.
 */
public interface JobApprovalRepository extends JpaRepository<JobApproval, Long> {
}
