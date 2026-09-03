package com.hirewise.be.service;

import com.hirewise.be.domain.Offer;
import com.hirewise.be.domain.OfferStatus;
import com.hirewise.be.dto.request.VerifyOfferOtpRequestDto;
import com.hirewise.be.dto.response.PublicOfferContentDto;
import com.hirewise.be.dto.response.PublicOfferSummaryDto;
import com.hirewise.be.event.OutboxEventPublisher;
import com.hirewise.be.event.OutboxEventType;
import com.hirewise.be.event.OutboxPayloads;
import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.InvalidTokenException;
import com.hirewise.be.logging.LogMaskUtils;
import com.hirewise.be.repository.OfferAccessTokenRepository;
import com.hirewise.be.repository.OfferRepository;
import com.hirewise.be.security.token.OfferAccessToken;
import com.hirewise.be.security.token.OpaqueTokenUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * M18 - Offer & e-Signature, candidate-facing access control: UC-38 (open
 * the secure link, verify the OTP, read the contract), BR-OFFER-03.
 * <p>
 * <strong>No RBAC here on purpose.</strong> Candidates have no account in
 * this system (SRS section 3.1), so there is no {@code CurrentUser} and
 * {@code AccessControlService} has nothing to check. Authentication is the
 * link token plus the one-time code, and every method below re-validates
 * both from scratch - the endpoints in
 * {@link com.hirewise.be.controller.PublicOfferController} are
 * {@code permitAll}.
 * <p>
 * Every failure to resolve a link raises the SAME
 * {@link ErrorCode#OFFER_LINK_INVALID}, whatever the actual cause, so an
 * attacker probing tokens cannot tell "no such token" from "wrong secret"
 * from "already signed".
 */
@Slf4j
@Service
public class OfferAccessService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int OTP_BOUND = 1_000_000;

    private final OfferRepository offerRepository;
    private final OfferAccessTokenRepository offerAccessTokenRepository;
    private final OutboxEventPublisher outboxEventPublisher;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final String productName;
    private final long otpTtlMinutes;
    private final int otpMaxAttempts;
    private final int otpResendLimit;
    private final long viewWindowMinutes;

    public OfferAccessService(OfferRepository offerRepository,
                               OfferAccessTokenRepository offerAccessTokenRepository,
                               OutboxEventPublisher outboxEventPublisher,
                               PasswordEncoder passwordEncoder,
                               Clock clock,
                               @Value("${app.mail.product-name:HireWise}") String productName,
                               @Value("${app.offer.otp-ttl-minutes:5}") long otpTtlMinutes,
                               @Value("${app.offer.otp-max-attempts:5}") int otpMaxAttempts,
                               @Value("${app.offer.otp-resend-limit:3}") int otpResendLimit,
                               @Value("${app.offer.view-window-minutes:30}") long viewWindowMinutes) {
        this.offerRepository = offerRepository;
        this.offerAccessTokenRepository = offerAccessTokenRepository;
        this.outboxEventPublisher = outboxEventPublisher;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.productName = productName;
        this.otpTtlMinutes = otpTtlMinutes;
        this.otpMaxAttempts = otpMaxAttempts;
        this.otpResendLimit = otpResendLimit;
        this.viewWindowMinutes = viewWindowMinutes;
    }

    /**
     * UC-38 step 1: the little the candidate may see before verifying - job
     * title, deadline and where the code will be sent. Carries no contract
     * terms (BR-OFFER-03).
     *
     * @param rawToken the raw link token from the URL
     * @return the pre-OTP summary
     * @throws InvalidTokenException    if the link cannot be resolved
     * @throws BusinessConflictException if the answer deadline has passed (EX-02, ME-32)
     */
    @Transactional
    public PublicOfferSummaryDto getSummary(String rawToken) {
        OfferAccessToken token = resolveToken(rawToken);
        Offer offer = token.getOffer();
        Instant now = Instant.now(clock);
        expireIfPastDeadline(offer, now);

        return PublicOfferSummaryDto.builder()
                .jobTitle(offer.getApplication().getJobPosition().getTitle())
                .companyName(productName)
                .candidateName(offer.getApplication().getCandidate().getFullName())
                .maskedEmail(LogMaskUtils.maskEmail(offer.getApplication().getCandidate().getPrimaryEmail()))
                .expiresAt(offer.getExpiresAt())
                .status(offer.getStatus().name())
                .otpVerified(token.isViewAllowed(now, viewWindowMinutes))
                .build();
    }

    /**
     * UC-38 step 2: issues a fresh one-time code and enqueues it by email
     * (BR-OFFER-03). Also backs the "Gui lai ma" control, which is why the
     * resend budget lives on the token row.
     *
     * @param rawToken the raw link token from the URL
     * @throws InvalidTokenException     if the link cannot be resolved
     * @throws BusinessConflictException if the answer deadline has passed (EX-02, ME-32)
     * @throws BadRequestException       if the resend budget is used up
     */
    @Transactional
    public void requestOtp(String rawToken) {
        OfferAccessToken token = resolveToken(rawToken);
        Offer offer = token.getOffer();
        Instant now = Instant.now(clock);
        expireIfPastDeadline(offer, now);

        if (token.getOtpSentCount() >= otpResendLimit) {
            throw new BadRequestException(ErrorCode.OFFER_OTP_RESEND_LIMIT);
        }

        String code = generateOtp();
        token.setOtpCodeHash(passwordEncoder.encode(code));
        token.setOtpExpiresAt(now.plus(otpTtlMinutes, ChronoUnit.MINUTES));
        // A new code invalidates the old one, so its wrong-guess budget resets too.
        token.setOtpAttempts(0);
        token.setOtpSentCount(token.getOtpSentCount() + 1);
        token.setOtpLastSentAt(now);
        offerAccessTokenRepository.save(token);

        outboxEventPublisher.publish(OutboxEventType.OFFER_OTP_EMAIL,
                OutboxPayloads.offerOtpEmail(
                        offer.getApplication().getCandidate().getPrimaryEmail(),
                        offer.getApplication().getCandidate().getFullName(),
                        offer.getApplication().getJobPosition().getTitle(),
                        code,
                        otpTtlMinutes));

        // The code itself is a credential and must never reach the log.
        log.info("Issued offer OTP {} of {} for offer {}",
                token.getOtpSentCount(), otpResendLimit, offer.getId());
    }

    /**
     * UC-38 steps 3-5: checks the code, records {@code otp_verified_at} and
     * returns the full contract terms.
     *
     * @param rawToken the raw link token from the URL
     * @param request  the 6-digit code the candidate typed
     * @return the offer's full terms
     * @throws InvalidTokenException     if the link cannot be resolved
     * @throws BusinessConflictException if the answer deadline has passed (EX-02, ME-32)
     * @throws BadRequestException       if the code is wrong/expired (EX-01, ME-33) or
     *                                    the attempt budget is used up
     */
    @Transactional
    public PublicOfferContentDto verifyOtp(String rawToken, VerifyOfferOtpRequestDto request) {
        OfferAccessToken token = resolveToken(rawToken);
        Offer offer = token.getOffer();
        Instant now = Instant.now(clock);
        expireIfPastDeadline(offer, now);

        if (token.getOtpAttempts() >= otpMaxAttempts) {
            throw new BadRequestException(ErrorCode.OFFER_OTP_ATTEMPTS_EXCEEDED);
        }
        boolean valid = token.getOtpCodeHash() != null
                && token.getOtpExpiresAt() != null
                && token.getOtpExpiresAt().isAfter(now)
                && passwordEncoder.matches(request.getCode(), token.getOtpCodeHash());
        if (!valid) {
            token.setOtpAttempts(token.getOtpAttempts() + 1);
            offerAccessTokenRepository.save(token);
            throw new BadRequestException(ErrorCode.OFFER_OTP_INVALID);
        }

        token.setOtpVerifiedAt(now);
        offerAccessTokenRepository.save(token);

        log.info("Offer {} OTP verified by candidate", offer.getId());

        return toContentDto(offer);
    }

    /**
     * UC-38 step 5 on a page reload: re-serves the terms while the previous
     * verification is still inside the viewing window, so the candidate does
     * not re-enter a code for every refresh.
     *
     * @param rawToken the raw link token from the URL
     * @return the offer's full terms
     * @throws BadRequestException if no OTP has been verified recently enough
     */
    @Transactional
    public PublicOfferContentDto getContent(String rawToken) {
        OfferAccessToken token = resolveToken(rawToken);
        Offer offer = token.getOffer();
        Instant now = Instant.now(clock);
        expireIfPastDeadline(offer, now);
        requireVerifiedOtp(token, now);

        return toContentDto(offer);
    }

    /**
     * Resolves a raw link token to its row, checking the secret half.
     * <p>
     * Package-private so {@code OfferSigningService} (UC-39) reuses the exact
     * same validation instead of re-implementing it.
     *
     * @param rawToken the raw {@code "<id>:<secret>"} token
     * @return the matching, still-usable token row
     * @throws InvalidTokenException for every failure mode, indistinguishably
     */
    OfferAccessToken resolveToken(String rawToken) {
        OpaqueTokenUtil.Parts parts = OpaqueTokenUtil.decode(rawToken);
        if (parts == null) {
            throw new InvalidTokenException(ErrorCode.OFFER_LINK_INVALID);
        }
        OfferAccessToken token = offerAccessTokenRepository.findById(parts.id())
                .orElseThrow(() -> new InvalidTokenException(ErrorCode.OFFER_LINK_INVALID));

        Instant now = Instant.now(clock);
        if (!token.isUsable(now) || !passwordEncoder.matches(parts.secret(), token.getTokenHash())) {
            throw new InvalidTokenException(ErrorCode.OFFER_LINK_INVALID);
        }
        return token;
    }

    /**
     * BR-OFFER-03: refuses to serve contract terms (or a signature) until an
     * OTP has been verified inside the viewing window.
     */
    void requireVerifiedOtp(OfferAccessToken token, Instant now) {
        if (!token.isViewAllowed(now, viewWindowMinutes)) {
            throw new BadRequestException(ErrorCode.OFFER_OTP_REQUIRED);
        }
    }

    /**
     * EX-02 / BR-OFFER-02: an Offer whose deadline passed before the expiry
     * worker got to it is flipped here, so the candidate can never read or
     * sign a stale offer just because the sweep runs every few minutes.
     */
    void expireIfPastDeadline(Offer offer, Instant now) {
        if (offer.getStatus() == OfferStatus.SENT && !offer.getExpiresAt().isAfter(now)) {
            offer.setStatus(OfferStatus.EXPIRED);
            offer.setUpdatedAt(now);
            offerRepository.save(offer);
            log.info("Offer {} expired on access (deadline {})", offer.getId(), offer.getExpiresAt());
        }
        if (offer.getStatus() == OfferStatus.EXPIRED) {
            throw new BusinessConflictException(ErrorCode.OFFER_EXPIRED);
        }
    }

    private PublicOfferContentDto toContentDto(Offer offer) {
        return PublicOfferContentDto.builder()
                .jobTitle(offer.getApplication().getJobPosition().getTitle())
                .companyName(productName)
                .candidateName(offer.getApplication().getCandidate().getFullName())
                .salary(offer.getSalary())
                .probationRate(offer.getProbationRate())
                .startDate(offer.getStartDate())
                .expiresAt(offer.getExpiresAt())
                .status(offer.getStatus().name())
                .renderedBody(offer.getRenderedBody())
                .signed(offer.getStatus() == OfferStatus.SIGNED)
                .signedAt(offer.getSignedAt())
                .build();
    }

    /** Six digits, zero-padded, from a cryptographically strong source. */
    private static String generateOtp() {
        return String.format("%06d", RANDOM.nextInt(OTP_BOUND));
    }
}
