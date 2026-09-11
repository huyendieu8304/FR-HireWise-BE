package com.hirewise.be.repository;

import com.hirewise.be.domain.InterviewBookingRequest;
import com.hirewise.be.domain.InterviewBookingRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link InterviewBookingRequest} entities (UC-25).
 */
public interface InterviewBookingRequestRepository extends JpaRepository<InterviewBookingRequest, Long> {

    /**
     * Looks up a booking request by its opaque link token. Used in the public-facing
     * booking page (UC-34) to validate and load the request without exposing the
     * internal primary key.
     *
     * @param bookingToken the UUID embedded in the candidate's emailed link
     * @return the matching request, or empty if not found / token unknown
     */
    @Query("""
            SELECT r FROM InterviewBookingRequest r
            JOIN FETCH r.application a
            JOIN FETCH a.candidate
            JOIN FETCH a.jobPosition j
            JOIN FETCH r.interviewer
            JOIN FETCH r.targetStage
            WHERE r.bookingToken = :token
            """)
    Optional<InterviewBookingRequest> findByBookingTokenFetch(@Param("token") UUID token);

    /** Returns all booking requests for an application (Recruiter view). */
    List<InterviewBookingRequest> findByApplication_IdOrderByCreatedAtDesc(UUID applicationId);

    /** Checks whether an OPEN booking request exists for a given application. */
    boolean existsByApplication_IdAndStatus(UUID applicationId, InterviewBookingRequestStatus status);
}
