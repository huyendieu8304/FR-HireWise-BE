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
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * A single candidate-selectable time slot within an {@link InterviewBookingRequest} (UC-25/UC-34/UC-35).
 * <p>
 * Slot locking (BR-SCHED-02): when a candidate clicks a slot, a short-lived HELD lock
 * is applied inside a pessimistic-write transaction before the confirmation screen is
 * shown. If the transaction does not complete within {@code held_until}, another
 * candidate may grab the slot.
 */
@Entity
@Table(name = "interview_booking_slots")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewBookingSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_request_id", nullable = false)
    private InterviewBookingRequest bookingRequest;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "slot_time", nullable = false)
    private LocalTime slotTime;

    /** Duration in minutes; defaults to 45 minutes in line with UC-24. */
    @Builder.Default
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes = 45;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private InterviewBookingSlotStatus status = InterviewBookingSlotStatus.OPEN;

    /**
     * Optimistic hold expiry — the row is only exclusively locked (HELD) until this
     * instant. After {@code held_until} passes, the slot can be grabbed by another
     * candidate (BR-SCHED-02).
     */
    @Column(name = "held_until")
    private Instant heldUntil;

    /** Timestamp when the candidate pressed [Xác nhận] (UC-35 step 1). */
    @Column(name = "selected_at")
    private Instant selectedAt;
}
