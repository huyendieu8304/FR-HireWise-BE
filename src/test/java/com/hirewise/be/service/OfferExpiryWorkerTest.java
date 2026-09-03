package com.hirewise.be.service;

import com.hirewise.be.domain.Offer;
import com.hirewise.be.domain.OfferStatus;
import com.hirewise.be.repository.OfferRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** BR-OFFER-02: Offers nobody ever opened must not sit as SENT forever. */
@ExtendWith(MockitoExtension.class)
class OfferExpiryWorkerTest {

    private static final Instant NOW = Instant.parse("2026-09-20T00:00:00Z");

    @Mock
    private OfferRepository offerRepository;

    private OfferExpiryWorker worker;

    @BeforeEach
    void setUp() {
        worker = new OfferExpiryWorker(offerRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void marksOverdueSentOffersExpired() {
        Offer overdue = offer(OfferStatus.SENT, Instant.parse("2026-09-15T00:00:00Z"));
        when(offerRepository.findByStatusAndExpiresAtBefore(OfferStatus.SENT, NOW))
                .thenReturn(List.of(overdue));

        worker.expireOverdueOffers();

        assertThat(overdue.getStatus()).isEqualTo(OfferStatus.EXPIRED);
        assertThat(overdue.getUpdatedAt()).isEqualTo(NOW);
        verify(offerRepository).saveAll(List.of(overdue));
    }

    @Test
    void onlyQueriesSentOffers_leavingDraftAndSettledOnesAlone() {
        when(offerRepository.findByStatusAndExpiresAtBefore(OfferStatus.SENT, NOW)).thenReturn(List.of());

        worker.expireOverdueOffers();

        // A DRAFT was never promised to anyone and a settled offer must not be
        // rewritten - both are excluded by the query, not filtered afterwards.
        verify(offerRepository).findByStatusAndExpiresAtBefore(OfferStatus.SENT, NOW);
        verify(offerRepository, never()).saveAll(any());
    }

    private static Offer offer(OfferStatus status, Instant expiresAt) {
        return Offer.builder()
                .id(UUID.randomUUID())
                .status(status)
                .expiresAt(expiresAt)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }
}
