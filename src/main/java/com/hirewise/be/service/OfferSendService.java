package com.hirewise.be.service;

import com.hirewise.be.domain.Offer;
import com.hirewise.be.domain.OfferStatus;
import com.hirewise.be.dto.response.OfferResponseDto;
import com.hirewise.be.event.OutboxEventPublisher;
import com.hirewise.be.event.OutboxEventType;
import com.hirewise.be.event.OutboxPayloads;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.mapper.OfferMapper;
import com.hirewise.be.repository.OfferAccessTokenRepository;
import com.hirewise.be.repository.OfferRepository;
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.security.token.OfferAccessToken;
import com.hirewise.be.security.token.OpaqueTokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * M18 - Offer & e-Signature: UC-37 (send the secure Offer link and request
 * an electronic signature, BR-OFFER-02/03). Layer 4 ownership is enforced
 * by {@code @RequiresOwnership} on
 * {@link com.hirewise.be.controller.OfferController} before this service is
 * entered, so {@link #send} does not repeat the RBAC check.
 */
@Slf4j
@Service
public class OfferSendService {

    private static final DateTimeFormatter VI_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final OfferRepository offerRepository;
    private final OfferAccessTokenRepository offerAccessTokenRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String linkBaseUrl;

    public OfferSendService(OfferRepository offerRepository,
                             OfferAccessTokenRepository offerAccessTokenRepository,
                             OutboxEventPublisher outboxEventPublisher,
                             PasswordEncoder passwordEncoder,
                             Clock clock,
                             @Value("${app.offer.link-base-url:http://localhost:5173/offer}") String linkBaseUrl) {
        this.offerRepository = offerRepository;
        this.offerAccessTokenRepository = offerAccessTokenRepository;
        this.outboxEventPublisher = outboxEventPublisher;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.linkBaseUrl = linkBaseUrl;
    }

    /**
     * UC-37 main flow: issues the candidate's secure link, moves the Offer to
     * {@code SENT} and enqueues EM-11 in the same transaction.
     * <p>
     * Also serves the [Gui lai] button of EX-01: calling this again on an
     * already-{@code SENT} Offer keeps the same token row - and therefore the
     * same OTP/verification state - but issues a fresh secret, so the link in
     * the earlier email stops working and only the newest one is valid.
     *
     * @param offerId     id of the Offer to send
     * @param currentUser authenticated caller (already ownership-checked by the controller)
     * @return the Offer in its new {@code SENT} state
     * @throws ResourceNotFoundException if no Offer exists with this id
     * @throws BusinessConflictException if the Offer is already settled
     *                                    (signed/declined/expired/cancelled) or its
     *                                    answer deadline has already passed
     */
    @Transactional
    public OfferResponseDto send(UUID offerId, CurrentUser currentUser) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.OFFER_NOT_FOUND, offerId));

        if (!offer.isActive()) {
            throw new BusinessConflictException(ErrorCode.OFFER_NOT_SENDABLE, offer.getStatus().name());
        }

        Instant now = Instant.now(clock);
        // BR-OFFER-02: sending an offer the candidate could never answer in
        // time would just produce a dead link and a confused candidate.
        if (!offer.getExpiresAt().isAfter(now)) {
            throw new BusinessConflictException(ErrorCode.OFFER_EXPIRED);
        }

        String rawToken = resolveOrCreateToken(offer, now);

        offer.setStatus(OfferStatus.SENT);
        offer.setSentAt(now);
        offer.setUpdatedAt(now);
        offerRepository.save(offer);

        // BR-OFFER-02/03 + UC-37 step 4: enqueue EM-11 in the same transaction
        // as the status change - see OutboxEventPublisher for why this beats
        // sending synchronously. EX-01 (SMTP down) is handled by its retries.
        outboxEventPublisher.publish(OutboxEventType.OFFER_SENT_EMAIL,
                OutboxPayloads.offerSentEmail(
                        offer.getApplication().getCandidate().getPrimaryEmail(),
                        offer.getApplication().getCandidate().getFullName(),
                        offer.getApplication().getJobPosition().getTitle(),
                        linkBaseUrl + "/" + rawToken,
                        VI_DATE_TIME_FORMATTER.format(offer.getExpiresAt()),
                        currentUser.fullName()));

        // The raw token is a credential - log the offer id only, never the link.
        log.info("Sent offer {} to candidate of application {} (expires at {})",
                offer.getId(), offer.getApplication().getId(), offer.getExpiresAt());

        return OfferMapper.toDto(offer);
    }

    /**
     * Returns the raw link token to email out, keeping the Offer's existing
     * token row when there is a usable one.
     * <p>
     * The raw secret only ever exists in memory at issue time - the DB holds
     * its hash - so a resend cannot re-send the original link and must issue
     * a new secret. Keeping the same row preserves the candidate's OTP and
     * verification state across a resend, and the {@code UNIQUE(offer_id)}
     * constraint keeps one live link per Offer.
     */
    private String resolveOrCreateToken(Offer offer, Instant now) {
        OfferAccessToken existing = offerAccessTokenRepository.findByOffer_Id(offer.getId()).orElse(null);
        if (existing != null && existing.isUsable(now)) {
            // Cannot recover the original secret from its hash, so rotate the
            // secret in place: same row (and same otp/verification state), new
            // link. The previous email's link stops working, which is the
            // safer trade-off versus being unable to resend at all.
            String secret = OpaqueTokenUtil.newSecret();
            existing.setTokenHash(passwordEncoder.encode(secret));
            existing.setExpiresAt(offer.getExpiresAt());
            offerAccessTokenRepository.save(existing);
            return OpaqueTokenUtil.encode(existing.getTokenId(), secret);
        }

        UUID tokenId = UUID.randomUUID();
        String secret = OpaqueTokenUtil.newSecret();
        OfferAccessToken token = OfferAccessToken.builder()
                .tokenId(tokenId)
                .offer(offer)
                .tokenHash(passwordEncoder.encode(secret))
                .expiresAt(offer.getExpiresAt())
                .otpAttempts(0)
                .otpSentCount(0)
                .createdAt(now)
                .build();
        offerAccessTokenRepository.save(token);
        return OpaqueTokenUtil.encode(tokenId, secret);
    }
}
