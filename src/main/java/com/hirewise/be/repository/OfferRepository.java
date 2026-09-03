package com.hirewise.be.repository;

import com.hirewise.be.domain.Offer;
import com.hirewise.be.domain.OfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence for {@link Offer} (UC-36..UC-39). */
public interface OfferRepository extends JpaRepository<Offer, UUID> {

    /**
     * BR-OFFER-01 pre-check: does this Application already have an Offer in
     * an active status? The partial unique index
     * {@code uk_offers_one_active_per_application} is the real guarantee -
     * this exists so the Recruiter gets a proper EX-01 business error rather
     * than a constraint violation.
     *
     * @param applicationId the Application being offered
     * @param statuses      the statuses considered active, see {@link Offer#ACTIVE_STATUSES}
     */
    boolean existsByApplication_IdAndStatusIn(UUID applicationId, Collection<OfferStatus> statuses);

    /** Most recent Offer of an Application, whatever its status - for the Applicant Card. */
    Optional<Offer> findFirstByApplication_IdOrderByCreatedAtDesc(UUID applicationId);

    /**
     * BR-OFFER-02: Offers whose answer deadline has passed while still
     * awaiting the candidate, picked up by {@code OfferExpiryWorker}.
     *
     * @param status the status to sweep, always {@link OfferStatus#SENT}
     * @param now    current instant
     */
    List<Offer> findByStatusAndExpiresAtBefore(OfferStatus status, Instant now);
}
