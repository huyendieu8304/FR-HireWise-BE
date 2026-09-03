package com.hirewise.be.service;

import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.Candidate;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.Offer;
import com.hirewise.be.domain.OfferStatus;
import com.hirewise.be.dto.request.VerifyOfferOtpRequestDto;
import com.hirewise.be.dto.response.PublicOfferContentDto;
import com.hirewise.be.dto.response.PublicOfferSummaryDto;
import com.hirewise.be.event.OutboxEventPublisher;
import com.hirewise.be.event.OutboxEventType;
import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.InvalidTokenException;
import com.hirewise.be.repository.OfferAccessTokenRepository;
import com.hirewise.be.repository.OfferRepository;
import com.hirewise.be.security.token.OfferAccessToken;
import com.hirewise.be.security.token.OpaqueTokenUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UC-38: open the secure Offer link and verify the OTP (BR-OFFER-03, ME-32/ME-33). */
@ExtendWith(MockitoExtension.class)
class OfferAccessServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-15T00:00:00Z");
    private static final UUID TOKEN_ID = UUID.randomUUID();
    private static final UUID OFFER_ID = UUID.randomUUID();
    private static final String SECRET = "a-high-entropy-secret";
    private static final long OTP_TTL_MINUTES = 5;
    private static final int OTP_MAX_ATTEMPTS = 5;
    private static final int OTP_RESEND_LIMIT = 3;
    private static final long VIEW_WINDOW_MINUTES = 30;

    @Mock
    private OfferRepository offerRepository;
    @Mock
    private OfferAccessTokenRepository offerAccessTokenRepository;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    private PasswordEncoder passwordEncoder;
    private OfferAccessService offerAccessService;
    private String rawToken;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        offerAccessService = new OfferAccessService(
                offerRepository,
                offerAccessTokenRepository,
                outboxEventPublisher,
                passwordEncoder,
                Clock.fixed(NOW, ZoneOffset.UTC),
                "HireWise",
                OTP_TTL_MINUTES,
                OTP_MAX_ATTEMPTS,
                OTP_RESEND_LIMIT,
                VIEW_WINDOW_MINUTES);

        rawToken = OpaqueTokenUtil.encode(TOKEN_ID, SECRET);
    }

    @Test
    void summaryHidesContractTermsUntilOtpIsVerified() {
        when(offerAccessTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(token(offer(OfferStatus.SENT))));

        PublicOfferSummaryDto summary = offerAccessService.getSummary(rawToken);

        assertThat(summary.getJobTitle()).isEqualTo("Backend Engineer");
        assertThat(summary.isOtpVerified()).isFalse();
        // Masked so the page - reachable by anyone holding the link - cannot leak it.
        assertThat(summary.getMaskedEmail()).isNotEqualTo("nguyenvana@example.com").contains("*");
    }

    @Test
    void malformedToken_throwsInvalidToken() {
        assertThatThrownBy(() -> offerAccessService.getSummary("not-a-token"))
                .isInstanceOf(InvalidTokenException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OFFER_LINK_INVALID);
    }

    @Test
    void unknownToken_throwsSameInvalidTokenAsWrongSecret() {
        when(offerAccessTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> offerAccessService.getSummary(rawToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OFFER_LINK_INVALID);
    }

    @Test
    void wrongSecret_throwsInvalidToken() {
        when(offerAccessTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(token(offer(OfferStatus.SENT))));

        assertThatThrownBy(() -> offerAccessService.getSummary(OpaqueTokenUtil.encode(TOKEN_ID, "wrong-secret")))
                .isInstanceOf(InvalidTokenException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OFFER_LINK_INVALID);
    }

    @Test
    void alreadyUsedLink_throwsInvalidToken() {
        OfferAccessToken token = token(offer(OfferStatus.SIGNED));
        token.setUsedAt(NOW);
        when(offerAccessTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> offerAccessService.getSummary(rawToken))
                .isInstanceOf(InvalidTokenException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OFFER_LINK_INVALID);
    }

    @Test
    void pastDeadline_marksOfferExpiredAndThrows() {
        Offer offer = offer(OfferStatus.SENT);
        offer.setExpiresAt(Instant.parse("2026-09-01T00:00:00Z"));
        OfferAccessToken token = token(offer);
        token.setExpiresAt(Instant.parse("2026-09-30T00:00:00Z"));
        when(offerAccessTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> offerAccessService.getSummary(rawToken))
                .isInstanceOf(BusinessConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OFFER_EXPIRED);

        // EX-02 / BR-OFFER-02: flipped on access, not left stale until the worker runs.
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.EXPIRED);
        verify(offerRepository).save(offer);
    }

    @Test
    void requestOtp_storesHashedCodeAndEnqueuesEmail() {
        OfferAccessToken token = token(offer(OfferStatus.SENT));
        when(offerAccessTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(token));
        when(offerAccessTokenRepository.save(any(OfferAccessToken.class))).thenAnswer(c -> c.getArgument(0));

        offerAccessService.requestOtp(rawToken);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventPublisher).publish(eq(OutboxEventType.OFFER_OTP_EMAIL), payload.capture());

        String code = (String) payload.getValue().get("otpCode");
        assertThat(code).matches("\\d{6}");
        assertThat(token.getOtpCodeHash()).isNotEqualTo(code);
        assertThat(passwordEncoder.matches(code, token.getOtpCodeHash())).isTrue();
        assertThat(token.getOtpSentCount()).isEqualTo(1);
        assertThat(token.getOtpExpiresAt()).isEqualTo(NOW.plus(OTP_TTL_MINUTES, ChronoUnit.MINUTES));
    }

    @Test
    void requestOtp_resetsWrongGuessBudgetForTheNewCode() {
        OfferAccessToken token = token(offer(OfferStatus.SENT));
        token.setOtpAttempts(4);
        when(offerAccessTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(token));
        when(offerAccessTokenRepository.save(any(OfferAccessToken.class))).thenAnswer(c -> c.getArgument(0));

        offerAccessService.requestOtp(rawToken);

        assertThat(token.getOtpAttempts()).isZero();
    }

    @Test
    void requestOtp_beyondResendLimit_throwsBadRequest() {
        OfferAccessToken token = token(offer(OfferStatus.SENT));
        token.setOtpSentCount(OTP_RESEND_LIMIT);
        when(offerAccessTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> offerAccessService.requestOtp(rawToken))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OFFER_OTP_RESEND_LIMIT);

        verify(outboxEventPublisher, never()).publish(any(), any());
    }

    @Test
    void verifyOtp_correctCode_recordsVerificationAndReturnsTerms() {
        Offer offer = offer(OfferStatus.SENT);
        OfferAccessToken token = tokenWithOtp(offer, "123456", NOW.plusSeconds(120));
        when(offerAccessTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(token));
        when(offerAccessTokenRepository.save(any(OfferAccessToken.class))).thenAnswer(c -> c.getArgument(0));

        PublicOfferContentDto content =
                offerAccessService.verifyOtp(rawToken, new VerifyOfferOtpRequestDto("123456"));

        assertThat(token.getOtpVerifiedAt()).isEqualTo(NOW);
        assertThat(content.getRenderedBody()).isEqualTo("<p>Offer terms</p>");
        assertThat(content.getSalary()).isEqualByComparingTo(new BigDecimal("25000000"));
    }

    @Test
    void verifyOtp_wrongCode_throwsAndCountsTheAttempt() {
        OfferAccessToken token = tokenWithOtp(offer(OfferStatus.SENT), "123456", NOW.plusSeconds(120));
        when(offerAccessTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(token));
        when(offerAccessTokenRepository.save(any(OfferAccessToken.class))).thenAnswer(c -> c.getArgument(0));

        assertThatThrownBy(() -> offerAccessService.verifyOtp(rawToken, new VerifyOfferOtpRequestDto("000000")))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OFFER_OTP_INVALID);

        assertThat(token.getOtpAttempts()).isEqualTo(1);
        assertThat(token.getOtpVerifiedAt()).isNull();
    }

    @Test
    void verifyOtp_expiredCode_throwsInvalid() {
        OfferAccessToken token = tokenWithOtp(offer(OfferStatus.SENT), "123456", NOW.minusSeconds(1));
        when(offerAccessTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(token));
        when(offerAccessTokenRepository.save(any(OfferAccessToken.class))).thenAnswer(c -> c.getArgument(0));

        assertThatThrownBy(() -> offerAccessService.verifyOtp(rawToken, new VerifyOfferOtpRequestDto("123456")))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OFFER_OTP_INVALID);
    }

    @Test
    void verifyOtp_beyondAttemptBudget_throwsAttemptsExceeded() {
        OfferAccessToken token = tokenWithOtp(offer(OfferStatus.SENT), "123456", NOW.plusSeconds(120));
        token.setOtpAttempts(OTP_MAX_ATTEMPTS);
        when(offerAccessTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> offerAccessService.verifyOtp(rawToken, new VerifyOfferOtpRequestDto("123456")))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OFFER_OTP_ATTEMPTS_EXCEEDED);
    }

    @Test
    void getContent_withoutVerifiedOtp_throwsOtpRequired() {
        when(offerAccessTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(token(offer(OfferStatus.SENT))));

        assertThatThrownBy(() -> offerAccessService.getContent(rawToken))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OFFER_OTP_REQUIRED);
    }

    @Test
    void getContent_afterViewWindowLapsed_throwsOtpRequired() {
        OfferAccessToken token = token(offer(OfferStatus.SENT));
        token.setOtpVerifiedAt(NOW.minus(VIEW_WINDOW_MINUTES + 1, ChronoUnit.MINUTES));
        when(offerAccessTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> offerAccessService.getContent(rawToken))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OFFER_OTP_REQUIRED);
    }

    @Test
    void getContent_insideViewWindow_returnsTermsWithoutRetypingCode() {
        OfferAccessToken token = token(offer(OfferStatus.SENT));
        token.setOtpVerifiedAt(NOW.minus(1, ChronoUnit.MINUTES));
        when(offerAccessTokenRepository.findById(TOKEN_ID)).thenReturn(Optional.of(token));

        assertThat(offerAccessService.getContent(rawToken).getRenderedBody()).isEqualTo("<p>Offer terms</p>");
    }

    private OfferAccessToken token(Offer offer) {
        return OfferAccessToken.builder()
                .tokenId(TOKEN_ID)
                .offer(offer)
                .tokenHash(passwordEncoder.encode(SECRET))
                .expiresAt(EXPIRES_AT)
                .otpAttempts(0)
                .otpSentCount(0)
                .createdAt(NOW)
                .build();
    }

    private OfferAccessToken tokenWithOtp(Offer offer, String code, Instant otpExpiresAt) {
        OfferAccessToken token = token(offer);
        token.setOtpCodeHash(passwordEncoder.encode(code));
        token.setOtpExpiresAt(otpExpiresAt);
        token.setOtpSentCount(1);
        return token;
    }

    private Offer offer(OfferStatus status) {
        Candidate candidate = new Candidate();
        candidate.setId(UUID.randomUUID());
        candidate.setFullName("Nguyen Van A");
        candidate.setPrimaryEmail("nguyenvana@example.com");

        JobPosition job = new JobPosition();
        job.setId(UUID.randomUUID());
        job.setTitle("Backend Engineer");

        Application application = Application.builder()
                .id(UUID.randomUUID())
                .candidate(candidate)
                .jobPosition(job)
                .appliedAt(NOW)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();

        return Offer.builder()
                .id(OFFER_ID)
                .application(application)
                .salary(new BigDecimal("25000000"))
                .probationRate(new BigDecimal("85.00"))
                .startDate(LocalDate.of(2026, 10, 1))
                .expiresAt(EXPIRES_AT)
                .status(status)
                .renderedBody("<p>Offer terms</p>")
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }
}
