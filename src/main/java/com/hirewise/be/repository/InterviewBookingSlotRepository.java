package com.hirewise.be.repository;

import com.hirewise.be.domain.InterviewBookingSlot;
import com.hirewise.be.domain.InterviewBookingSlotStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for {@link InterviewBookingSlot} entities (UC-25/UC-34/UC-35).
 */
public interface InterviewBookingSlotRepository extends JpaRepository<InterviewBookingSlot, Long> {

    List<InterviewBookingSlot> findByBookingRequestIdOrderBySlotDateAscSlotTimeAsc(Long bookingRequestId);

    List<InterviewBookingSlot> findByBookingRequestIdAndStatus(Long bookingRequestId, InterviewBookingSlotStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM InterviewBookingSlot s WHERE s.id = :slotId")
    Optional<InterviewBookingSlot> findByIdForUpdate(@Param("slotId") Long slotId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM InterviewBookingSlot s
            JOIN FETCH s.bookingRequest r
            JOIN FETCH r.application a
            JOIN FETCH a.candidate c
            JOIN FETCH a.jobPosition j
            JOIN FETCH r.interviewer i
            WHERE s.id = :slotId
            """)
    Optional<InterviewBookingSlot> findByIdWithDetailsForUpdate(@Param("slotId") Long slotId);
}
