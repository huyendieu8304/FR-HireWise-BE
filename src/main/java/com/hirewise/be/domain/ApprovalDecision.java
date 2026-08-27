package com.hirewise.be.domain;

/**
 * Outcome of a {@link JobApproval} (LV-04). {@code null} on the
 * {@code decision} column means the request is still pending (UC-14).
 */
public enum ApprovalDecision {
    APPROVED,
    REJECTED
}
