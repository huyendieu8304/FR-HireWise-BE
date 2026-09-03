package com.hirewise.be.service;

import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.Candidate;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.Offer;
import com.hirewise.be.domain.OfferStatus;
import com.hirewise.be.domain.OfferTemplate;
import com.hirewise.be.event.OutboxEventPublisher;
import com.hirewise.be.event.OutboxEventType;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.repository.OfferAccessTokenRepository;
import com.hirewise.be.repository.OfferRepository;
import com.hirewise.be.security.CurrentUser;
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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UC-37: send the secure Offer link + e-signature request (BR-OFFER-02/03). */
@ExtendWith(MockitoExtension.class)
class OfferSendServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-15T00:00:00Z");
    private static final UUID OFFER_ID = UUID.randomUUID();
    private static final String LINK_BASE_URL = "http://localhost:5173/offer";

    @Mock
    private OfferRepository offerRepository;
    @Mock
    private OfferAccessTokenRepository offerAccessTokenRepository;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    private PasswordEncoder passwordEncoder;
    private OfferSendService offerSendService;
    private CurrentUser recruiter;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        offerSendService = new OfferSendService(
                offerRepository,
                offerAccessTokenRepository,
                outboxEventPublisher,
                passwordEncoder,
                Clock.fixed(NOW, ZoneOffset.UTC),
                LINK_BASE_URL);

        recruiter = new CurrentUser(7L, "recruiter@hirewise.local", "Le Thi Recruiter", Set.of("RECRUITER"));
    }

    @Test
    void marksOfferSentAndEnqueuesOfferEmailWithSecureLink() {
        Offer offer = offer(OfferStatus.DRAFT);
        when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(offer));
        when(offerAccessTokenRepository.findByOffer_Id(OFFER_ID)).thenReturn(Optional.empty());
        when(offerAccessTokenRepository.save(any(OfferAccessToken.class)))
                .thenAnswer(call -> call.getArgument(0));
        when(offerRepository.save(any(Offer.class))).thenAnswer(call -> call.getArgument(0));

        offerSendService.send(OFFER_ID, recruiter);

        assertThat(offer.getStatus()).isEqualTo(OfferStatus.SENT);
        assertThat(offer.getSentAt()).isEqualTo(NOW);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventPublisher).publish(eq(OutboxEventType.OFFER_SENT_EMAIL), payload.capture());
        assertThat(payload.getValue().get("email")).isEqualTo("candidate@example.com");
        assertThat(payload.getValue().get("jobTitle")).isEqualTo("Backend Engineer");
        assertThat((String) payload.getValue().get("offerLink")).startsWith(LINK_BASE_URL + "/");
    }

    @Test
    void storesOnlyTheTokenHashNeverTheRawSecret() {
        Offer offer = offer(OfferStatus.DRAFT);
        when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(offer));
        when(offerAccessTokenRepository.findByOffer_Id(OFFER_ID)).thenReturn(Optional.empty());
        when(offerAccessTokenRepository.save(any(OfferAccessToken.class)))
                .thenAnswer(call -> call.getArgument(0));
        when(offerRepository.save(any(Offer.class))).thenAnswer(call -> call.getArgument(0));

        offerSendService.send(OFFER_ID, recruiter);

        ArgumentCaptor<OfferAccessToken> saved = ArgumentCaptor.forClass(OfferAccessToken.class);
        verify(offerAccessTokenRepository).save(saved.capture());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventPublisher).publish(eq(OutboxEventType.OFFER_SENT_EMAIL), payload.capture());

        String rawToken = ((String) payload.getValue().get("offerLink")).substring(LINK_BASE_URL.length() + 1);
        OpaqueTokenUtil.Parts parts = OpaqueTokenUtil.decode(rawToken);

        assertThat(parts).isNotNull();
        assertThat(parts.id()).isEqualTo(saved.getValue().getTokenId());
        assertThat(saved.getValue().getTokenHash()).isNotEqualTo(parts.secret());
        assertThat(passwordEncoder.matches(parts.secret(), saved.getValue().getTokenHash())).isTrue();
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(EXPIRES_AT);
    }

    @Test
    void resend_keepsSameTokenRowButIssuesFreshSecret() {
        Offer offer = offer(OfferStatus.SENT);
        UUID existingTokenId = UUID.randomUUID();
        OfferAccessToken existing = OfferAccessToken.builder()
                .tokenId(existingTokenId)
                .offer(offer)
                .tokenHash(passwordEncoder.encode("old-secret"))
                .expiresAt(EXPIRES_AT)
                .otpVerifiedAt(NOW)
                .createdAt(NOW)
                .build();

        when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(offer));
        when(offerAccessTokenRepository.findByOffer_Id(OFFER_ID)).thenReturn(Optional.of(existing));
        when(offerAccessTokenRepository.save(any(OfferAccessToken.class)))
                .thenAnswer(call -> call.getArgument(0));
        when(offerRepository.save(any(Offer.class))).thenAnswer(call -> call.getArgument(0));

        offerSendService.send(OFFER_ID, recruiter);

        assertThat(existing.getTokenId()).isEqualTo(existingTokenId);
        // OTP state survives a resend - the candidate need not re-verify.
        assertThat(existing.getOtpVerifiedAt()).isEqualTo(NOW);
        assertThat(passwordEncoder.matches("old-secret", existing.getTokenHash())).isFalse();
    }

    @Test
    void alreadySignedOffer_throwsBusinessConflict() {
        when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(offer(OfferStatus.SIGNED)));

        assertThatThrownBy(() -> offerSendService.send(OFFER_ID, recruiter))
                .isInstanceOf(BusinessConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OFFER_NOT_SENDABLE);

        verify(outboxEventPublisher, never()).publish(any(), any());
    }

    @Test
    void deadlineAlreadyPassed_throwsBusinessConflict() {
        Offer offer = offer(OfferStatus.DRAFT);
        offer.setExpiresAt(Instant.parse("2026-09-01T00:00:00Z"));
        when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> offerSendService.send(OFFER_ID, recruiter))
                .isInstanceOf(BusinessConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OFFER_EXPIRED);

        verify(outboxEventPublisher, never()).publish(any(), any());
    }

    @Test
    void offerNotFound_throwsResourceNotFound() {
        when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> offerSendService.send(OFFER_ID, recruiter))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OFFER_NOT_FOUND);
    }

    private Offer offer(OfferStatus status) {
        Candidate candidate = new Candidate();
        candidate.setId(UUID.randomUUID());
        candidate.setFullName("Nguyen Van A");
        candidate.setPrimaryEmail("candidate@example.com");

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
                .offerTemplate(OfferTemplate.builder().id(1L).name("Thu moi chuan").version(1).build())
                .salary(new BigDecimal("25000000"))
                .probationRate(new BigDecimal("85.00"))
                .startDate(LocalDate.of(2026, 10, 1))
                .expiresAt(EXPIRES_AT)
                .status(status)
                .renderedBody("<p>Offer</p>")
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }
}
