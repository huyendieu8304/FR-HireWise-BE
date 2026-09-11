package com.hirewise.be.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents a Self-service Booking request sent by a Recruiter to a candidate (UC-25).
 * <p>
 * The {@code booking_token} is the UUID embedded in the candidate-facing link
 * ({@code /booking/{token}}). It is generated once at creation and never changes.
 * The {@code expires_at} field controls how long the link stays valid (BR-SCHED-02).
 */
@Entity
@Table(name = "interview_booking_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewBookingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "interviewer_id", nullable = false)
    private User interviewer;

    /** Recruiter-specified date window start. */
    @Column(name = "date_range_start", nullable = false)
    private LocalDate dateRangeStart;

    /** Recruiter-specified date window end. */
    @Column(name = "date_range_end", nullable = false)
    private LocalDate dateRangeEnd;

    /**
     * The opaque URL-safe token placed in the candidate's booking link.
     * Deliberately a UUID (not a sequential id) so it cannot be enumerated.
     */
    @Column(name = "booking_token", nullable = false, unique = true, updatable = false)
    private UUID bookingToken;

    /** Timestamp after which this link becomes invalid (BR-SCHED-02). */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InterviewBookingRequestStatus status = InterviewBookingRequestStatus.OPEN;

    /** Target pipeline stage to transition the application to when slot is confirmed. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_stage_id")
    private PipelineStage targetStage;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private InterviewMode mode;

    @Column(name = "location_or_link", columnDefinition = "TEXT")
    private String locationOrLink;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Builder.Default
    @OneToMany(mappedBy = "bookingRequest", fetch = FetchType.LAZY)
    private List<InterviewBookingSlot> slots = new ArrayList<>();
}
