package com.hirewise.be.domain;

/**
 * Lifecycle status of a {@link JobPosition} (LV-03, SRS section 5.4.3).
 * <ul>
 *   <li>{@code DRAFT} - being composed by a Recruiter (UC-12); not visible to
 *       anyone else yet.</li>
 *   <li>{@code PENDING_APPROVAL} - submitted with a Pipeline Template attached
 *       (UC-13), waiting on the Hiring Manager (UC-14).</li>
 *   <li>{@code APPROVED} - Hiring Manager approved it (UC-15); eligible to be
 *       published (BR-APR-03) but not yet visible on the public Job Board.</li>
 *   <li>{@code REJECTED} - Hiring Manager rejected it (UC-15) with a reason;
 *       back with the Recruiter for edits (BR-JOB-04).</li>
 *   <li>{@code PUBLISHED} - live on the public Job Board (UC-16) and accepting
 *       applications (UC-17).</li>
 *   <li>{@code PAUSED} - temporarily hidden from the Job Board without losing
 *       Approved status; can be published again later.</li>
 *   <li>{@code CLOSED} - filled or cancelled; no longer accepts applications.</li>
 * </ul>
 */
public enum JobStatus {
    DRAFT,
    PENDING_APPROVAL,
    APPROVED,
    REJECTED,
    PUBLISHED,
    PAUSED,
    CLOSED
}
