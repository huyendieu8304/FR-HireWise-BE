package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * The rejection event for one {@link Application} (UC-29): which
 * standardized {@link RejectionReason} the Recruiter picked, their optional
 * free-text note, who did it and when. Kept as its own append-style table
 * rather than columns on {@code applications} - same reasoning as
 * {@link JobApproval} - with at most one row per Application ever
 * (BR-REJ-03: Refused is terminal, no revert; see the unique constraint on
 * {@code application_id} in V28).
 */
@Entity
@Table(name = "application_rejections")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationRejection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rejection_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reason_id", nullable = false)
    private RejectionReason reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rejected_by_user_id")
    private User rejectedBy;

    /** BR-REJ-01: optional note the Recruiter adds alongside the standardized reason. */
    @Column(name = "custom_message", columnDefinition = "text")
    private String customMessage;

    @Column(name = "rejected_at", nullable = false)
    private Instant rejectedAt;
}
