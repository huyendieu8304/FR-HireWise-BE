package com.hirewise.be.repository;

import com.hirewise.be.security.token.OfferAccessToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Persistence for the candidate's secure Offer link (UC-37/UC-38). */
public interface OfferAccessTokenRepository extends JpaRepository<OfferAccessToken, UUID> {

    /**
     * The live link of an Offer, if one was already issued - lets UC-37's
     * [Gui lai] reuse the token already in the candidate's inbox instead of
     * minting a second one that would silently invalidate the first.
     *
     * @param offerId id of the offer
     */
    Optional<OfferAccessToken> findByOffer_Id(UUID offerId);
}
