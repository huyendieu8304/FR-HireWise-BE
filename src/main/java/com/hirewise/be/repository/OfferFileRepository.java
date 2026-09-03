package com.hirewise.be.repository;

import com.hirewise.be.domain.OfferFile;
import com.hirewise.be.domain.OfferFileRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Persistence linking generated Offer artifacts to their {@code files} rows (UC-39). */
public interface OfferFileRepository extends JpaRepository<OfferFile, Long> {

    /**
     * One artifact of an Offer by role - e.g. the signed PDF for the
     * Recruiter's download link.
     *
     * @param offerId  id of the offer
     * @param fileRole which artifact is wanted
     */
    Optional<OfferFile> findFirstByOffer_IdAndFileRoleOrderByCreatedAtDesc(UUID offerId, OfferFileRole fileRole);
}
