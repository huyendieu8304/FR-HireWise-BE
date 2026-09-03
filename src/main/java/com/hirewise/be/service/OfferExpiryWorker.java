package com.hirewise.be.service;

import com.hirewise.be.domain.Offer;
import com.hirewise.be.domain.OfferStatus;
import com.hirewise.be.repository.OfferRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * BR-OFFER-02: sweeps Offers whose answer deadline passed while still
 * awaiting the candidate and moves them to {@link OfferStatus#EXPIRED}.
 * <p>
 * {@link OfferAccessService} already expires an Offer the moment a candidate
 * touches a stale link, so this worker exists for the Offer nobody opens at
 * all - without it such a row would sit as {@code SENT} forever and keep
 * blocking a replacement Offer under BR-OFFER-01.
 * <p>
 * Same {@code @Scheduled} shape as {@code CloudStorageTokenRefreshWorker}
 * and {@code OutboxDispatcher}.
 */
@Slf4j
@Component
public class OfferExpiryWorker {

    private final OfferRepository offerRepository;
    private final Clock clock;

    public OfferExpiryWorker(OfferRepository offerRepository, Clock clock) {
        this.offerRepository = offerRepository;
        this.clock = clock;
    }

    /**
     * Marks every overdue {@code SENT} Offer as {@code EXPIRED}.
     * <p>
     * Only {@code SENT} rows are touched: a {@code DRAFT} was never promised
     * to anyone and a settled Offer must not be rewritten.
     */
    @Scheduled(fixedDelayString = "${app.offer.expiry-poll-interval-ms:300000}")
    @Transactional
    public void expireOverdueOffers() {
        Instant now = Instant.now(clock);
        List<Offer> overdue = offerRepository.findByStatusAndExpiresAtBefore(OfferStatus.SENT, now);
        if (overdue.isEmpty()) {
            return;
        }

        for (Offer offer : overdue) {
            offer.setStatus(OfferStatus.EXPIRED);
            offer.setUpdatedAt(now);
            log.info("Offer {} expired without a signature (deadline {})", offer.getId(), offer.getExpiresAt());
        }
        offerRepository.saveAll(overdue);
    }
}
