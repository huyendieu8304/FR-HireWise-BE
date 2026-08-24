package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A person who has applied to at least one {@link JobPosition} (UC-17).
 * System-wide, independent of any single Job - one Candidate may have many
 * {@link Application}s. {@link #primaryEmail} is unique so a repeat applicant
 * (BR-APPLY-02) reuses this row instead of creating a duplicate.
 */
@Entity
@Table(name = "candidates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Candidate {

    @Id
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    /** Identity key for reuse/dedup across applications (BR-APPLY-02). */
    @Column(name = "primary_email", nullable = false, length = 255)
    private String primaryEmail;

    @Column(nullable = false, length = 30)
    private String phone;

    /** {@code BLACKLISTED} candidates still surface a warning rather than being auto-rejected (BR-APPLY-03). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CandidateStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
