package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One approval decision trail entry for a {@link JobPosition} (UC-14/UC-15).
 * Kept as its own append-style table - rather than a single column on
 * {@code job_positions} - so a resubmit-after-rejection cycle keeps every
 * past decision and reason instead of overwriting it.
 */
@Entity
@Table(name = "job_approvals")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_approval_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_position_id", nullable = false)
    private JobPosition jobPosition;

    /** {@code null} while still pending (UC-14); set by UC-15. */
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ApprovalDecision decision;

    /** BR-APR-02: required (>= 10 chars, enforced in the service layer) when {@link #decision} is REJECTED. */
    @Column(columnDefinition = "text")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_user_id")
    private User decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
