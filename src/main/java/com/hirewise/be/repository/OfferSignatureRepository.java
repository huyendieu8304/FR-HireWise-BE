package com.hirewise.be.repository;

import com.hirewise.be.domain.OfferSignature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Persistence for the e-signature evidence record (UC-39, BR-OFFER-04). */
public interface OfferSignatureRepository extends JpaRepository<OfferSignature, Long> {

    /** The Offer's signature, if it has been signed - at most one exists (BR-OFFER-04). */
    Optional<OfferSignature> findByOffer_Id(UUID offerId);
}
