package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One entry in the standardized rejection-reason catalog (UC-29, BR-REJ-01)
 * a Recruiter must pick from when rejecting an {@link Application} - keeps
 * {@code application_rejections} reportable instead of every Recruiter
 * free-typing their own wording. Seeded in V28; {@link #active} lets an
 * outdated reason stop appearing as a new choice without breaking past
 * {@link ApplicationRejection} rows that already reference it.
 */
@Entity
@Table(name = "rejection_reasons")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RejectionReason {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reason_id")
    private Long id;

    /** Stable technical code, unique system-wide, e.g. {@code TECHNICAL_GAP}. */
    @Column(nullable = false, length = 50)
    private String code;

    /** Human-readable label shown in the Recruiter's reject dropdown. */
    @Column(nullable = false, length = 150)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RejectionCategory category;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
