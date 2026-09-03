package com.hirewise.be.service;

import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.ApplicationStageHistory;
import com.hirewise.be.domain.ApplicationStatus;
import com.hirewise.be.domain.Candidate;
import com.hirewise.be.domain.FileStatus;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.Offer;
import com.hirewise.be.domain.OfferFile;
import com.hirewise.be.domain.OfferFileRole;
import com.hirewise.be.domain.OfferSignature;
import com.hirewise.be.domain.OfferStatus;
import com.hirewise.be.domain.PipelineStage;
import com.hirewise.be.domain.PipelineTemplate;
import com.hirewise.be.domain.SignatureMethod;
import com.hirewise.be.domain.StageTransitionType;
import com.hirewise.be.domain.StageType;
import com.hirewise.be.domain.StoredFile;
import com.hirewise.be.dto.request.SignOfferRequestDto;
import com.hirewise.be.dto.response.PublicOfferContentDto;
import com.hirewise.be.event.OutboxEventPublisher;
import com.hirewise.be.event.OutboxEventType;
import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.repository.ApplicationRepository;
import com.hirewise.be.repository.ApplicationStageHistoryRepository;
import com.hirewise.be.repository.OfferAccessTokenRepository;
import com.hirewise.be.repository.OfferFileRepository;
import com.hirewise.be.repository.OfferRepository;
import com.hirewise.be.repository.OfferSignatureRepository;
import com.hirewise.be.repository.PipelineStageRepository;
import com.hirewise.be.security.token.OfferAccessToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UC-39: the candidate signs electronically (BR-OFFER-04, ME-34). */
@ExtendWith(MockitoExtension.class)
class OfferSigningServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-09-15T00:00:00Z");
    private static final UUID OFFER_ID = UUID.randomUUID();
    private static final UUID APPLICATION_ID = UUID.randomUUID();
    private static final Long PIPELINE_TEMPLATE_ID = 3L;
    private static final String RAW_TOKEN = "raw-token";
    private static final String CLIENT_IP = "203.0.113.7";
    private static final String SIGNATURE_PNG =
            "data:image/png;base64," + Base64.getEncoder().encodeToString("fake-png-bytes".getBytes());

    @Mock
    private OfferAccessService offerAccessService;
    @Mock
    private OfferRepository offerRepository;
    @Mock
    private OfferAccessTokenRepository offerAccessTokenRepository;
    @Mock
    private OfferSignatureRepository offerSignatureRepository;
    @Mock
    private OfferFileRepository offerFileRepository;
    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private ApplicationStageHistoryRepository applicationStageHistoryRepository;
    @Mock
    private PipelineStageRepository pipelineStageRepository;
    @Mock
    private OfferPdfRenderer offerPdfRenderer;
    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    private OfferSigningService offerSigningService;

    @BeforeEach
    void setUp() {
        offerSigningService = new OfferSigningService(
                offerAccessService,
                offerRepository,
                offerAccessTokenRepository,
                offerSignatureRepository,
                offerFileRepository,
                applicationRepository,
                applicationStageHistoryRepository,
                pipelineStageRepository,
                offerPdfRenderer,
                fileStorageService,
                outboxEventPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC),
                "HireWise");
    }

    @Test
    void drawnSignature_storesEvidencePdfAndMovesApplicationToHired() {
        Offer offer = offer(OfferStatus.SENT);
        OfferAccessToken token = verifiedToken(offer);
        PipelineStage hiredStage = stage(20L, "Da tuyen", StageType.TERMINAL_SUCCESS);
        StoredFile signedFile = storedFile();

        when(offerAccessService.resolveToken(RAW_TOKEN)).thenReturn(token);
        when(pipelineStageRepository.findFirstByPipelineTemplate_IdAndStageTypeAndActiveTrue(
                PIPELINE_TEMPLATE_ID, StageType.TERMINAL_SUCCESS)).thenReturn(Optional.of(hiredStage));
        when(offerPdfRenderer.render(eq(offer), eq(SignatureMethod.DRAW), anyString(), anyString(), eq(NOW)))
                .thenReturn("pdf".getBytes());
        when(fileStorageService.store(any(), anyString(), eq("application/pdf"), anyString(), anyString()))
                .thenReturn(signedFile);
        when(fileStorageService.getViewUrl(signedFile)).thenReturn("https://drive.example/signed.pdf");

        PublicOfferContentDto result =
                offerSigningService.sign(RAW_TOKEN, drawRequest(), CLIENT_IP);

        assertThat(result.isSigned()).isTrue();
        assertThat(offer.getStatus()).isEqualTo(OfferStatus.SIGNED);
        assertThat(offer.getSignedAt()).isEqualTo(NOW);

        Application application = offer.getApplication();
        assertThat(application.getStatus()).isEqualTo(ApplicationStatus.HIRED);
        assertThat(application.getCurrentStage()).isEqualTo(hiredStage);

        // The link is retired so the signed contract page stops being reachable.
        assertThat(token.getUsedAt()).isEqualTo(NOW);
    }

    @Test
    void recordsSignatureEvidenceIncludingIpAndOtpTimestamp() {
        Offer offer = offer(OfferStatus.SENT);
        OfferAccessToken token = verifiedToken(offer);
        stubHappyPath(offer, token);

        offerSigningService.sign(RAW_TOKEN, drawRequest(), CLIENT_IP);

        ArgumentCaptor<OfferSignature> saved = ArgumentCaptor.forClass(OfferSignature.class);
        verify(offerSignatureRepository).save(saved.capture());

        assertThat(saved.getValue().getMethod()).isEqualTo(SignatureMethod.DRAW);
        assertThat(saved.getValue().getIpAddress()).isEqualTo(CLIENT_IP);
        assertThat(saved.getValue().getSignedAt()).isEqualTo(NOW);
        assertThat(saved.getValue().getOtpVerifiedAt()).isEqualTo(token.getOtpVerifiedAt());
        assertThat(saved.getValue().getSignerName()).isEqualTo("Nguyen Van A");
    }

    @Test
    void linksSignedPdfToTheOfferWithSignedRole() {
        Offer offer = offer(OfferStatus.SENT);
        stubHappyPath(offer, verifiedToken(offer));

        offerSigningService.sign(RAW_TOKEN, drawRequest(), CLIENT_IP);

        ArgumentCaptor<OfferFile> saved = ArgumentCaptor.forClass(OfferFile.class);
        verify(offerFileRepository).save(saved.capture());
        assertThat(saved.getValue().getFileRole()).isEqualTo(OfferFileRole.OFFER_SIGNED);
    }

    @Test
    void writesSystemStageHistoryWithNoActingUser() {
        Offer offer = offer(OfferStatus.SENT);
        stubHappyPath(offer, verifiedToken(offer));

        offerSigningService.sign(RAW_TOKEN, drawRequest(), CLIENT_IP);

        ArgumentCaptor<ApplicationStageHistory> saved = ArgumentCaptor.forClass(ApplicationStageHistory.class);
        verify(applicationStageHistoryRepository).save(saved.capture());

        // UC-39 step 6: the move is automatic, so nobody performed it.
        assertThat(saved.getValue().getTransitionType()).isEqualTo(StageTransitionType.SYSTEM);
        assertThat(saved.getValue().getChangedBy()).isNull();
    }

    @Test
    void enqueuesSignedConfirmationEmail() {
        Offer offer = offer(OfferStatus.SENT);
        stubHappyPath(offer, verifiedToken(offer));

        offerSigningService.sign(RAW_TOKEN, drawRequest(), CLIENT_IP);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventPublisher).publish(eq(OutboxEventType.OFFER_SIGNED_EMAIL), payload.capture());
        assertThat(payload.getValue().get("email")).isEqualTo("nguyenvana@example.com");
        assertThat(payload.getValue().get("signedFileLink")).isEqualTo("https://drive.example/signed.pdf");
    }

    @Test
    void typedSignature_usesTheTypedName() {
        Offer offer = offer(OfferStatus.SENT);
        OfferAccessToken token = verifiedToken(offer);
        StoredFile signedFile = storedFile();

        when(offerAccessService.resolveToken(RAW_TOKEN)).thenReturn(token);
        when(pipelineStageRepository.findFirstByPipelineTemplate_IdAndStageTypeAndActiveTrue(
                PIPELINE_TEMPLATE_ID, StageType.TERMINAL_SUCCESS))
                .thenReturn(Optional.of(stage(20L, "Da tuyen", StageType.TERMINAL_SUCCESS)));
        when(offerPdfRenderer.render(eq(offer), eq(SignatureMethod.TYPE), eq("Nguyen Van A Ky"), eq(null), eq(NOW)))
                .thenReturn("pdf".getBytes());
        when(fileStorageService.store(any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(signedFile);
        when(fileStorageService.getViewUrl(signedFile)).thenReturn("https://drive.example/signed.pdf");

        SignOfferRequestDto request = new SignOfferRequestDto(SignatureMethod.TYPE, null, "  Nguyen Van A Ky  ");
        offerSigningService.sign(RAW_TOKEN, request, CLIENT_IP);

        ArgumentCaptor<OfferSignature> saved = ArgumentCaptor.forClass(OfferSignature.class);
        verify(offerSignatureRepository).save(saved.capture());
        assertThat(saved.getValue().getSignerName()).isEqualTo("Nguyen Van A Ky");
    }

    @Test
    void blankTypedName_throwsSignatureRequired() {
        Offer offer = offer(OfferStatus.SENT);
        when(offerAccessService.resolveToken(RAW_TOKEN)).thenReturn(verifiedToken(offer));

        SignOfferRequestDto request = new SignOfferRequestDto(SignatureMethod.TYPE, null, "   ");

        assertThatThrownBy(() -> offerSigningService.sign(RAW_TOKEN, request, CLIENT_IP))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OFFER_SIGNATURE_REQUIRED);

        verify(offerSignatureRepository, never()).save(any());
    }

    @Test
    void emptyCanvas_throwsSignatureRequired() {
        Offer offer = offer(OfferStatus.SENT);
        when(offerAccessService.resolveToken(RAW_TOKEN)).thenReturn(verifiedToken(offer));

        SignOfferRequestDto request = new SignOfferRequestDto(SignatureMethod.DRAW, "   ", null);

        assertThatThrownBy(() -> offerSigningService.sign(RAW_TOKEN, request, CLIENT_IP))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OFFER_SIGNATURE_REQUIRED);
    }

    @Test
    void unreadableSignatureImage_throwsImageInvalid() {
        Offer offer = offer(OfferStatus.SENT);
        when(offerAccessService.resolveToken(RAW_TOKEN)).thenReturn(verifiedToken(offer));

        SignOfferRequestDto request =
                new SignOfferRequestDto(SignatureMethod.DRAW, "data:image/png;base64,!!!not-base64!!!", null);

        assertThatThrownBy(() -> offerSigningService.sign(RAW_TOKEN, request, CLIENT_IP))
                .isInstanceOf(BadRequestException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OFFER_SIGNATURE_IMAGE_INVALID);
    }

    @Test
    void alreadySigned_throwsBusinessConflict() {
        Offer offer = offer(OfferStatus.SIGNED);
        when(offerAccessService.resolveToken(RAW_TOKEN)).thenReturn(verifiedToken(offer));

        assertThatThrownBy(() -> offerSigningService.sign(RAW_TOKEN, drawRequest(), CLIENT_IP))
                .isInstanceOf(BusinessConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OFFER_ALREADY_SIGNED);

        verify(offerSignatureRepository, never()).save(any());
    }

    @Test
    void draftOfferNeverSentToCandidate_throwsNotSignable() {
        Offer offer = offer(OfferStatus.DRAFT);
        when(offerAccessService.resolveToken(RAW_TOKEN)).thenReturn(verifiedToken(offer));

        assertThatThrownBy(() -> offerSigningService.sign(RAW_TOKEN, drawRequest(), CLIENT_IP))
                .isInstanceOf(BusinessConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OFFER_NOT_SIGNABLE);
    }

    @Test
    void pipelineWithoutTerminalSuccessStage_throwsBeforeRenderingAnything() {
        Offer offer = offer(OfferStatus.SENT);
        when(offerAccessService.resolveToken(RAW_TOKEN)).thenReturn(verifiedToken(offer));
        when(pipelineStageRepository.findFirstByPipelineTemplate_IdAndStageTypeAndActiveTrue(
                PIPELINE_TEMPLATE_ID, StageType.TERMINAL_SUCCESS)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> offerSigningService.sign(RAW_TOKEN, drawRequest(), CLIENT_IP))
                .isInstanceOf(BusinessConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PIPELINE_MISSING_TERMINAL_SUCCESS_STAGE);

        // No orphaned upload is left behind on Cloud Storage.
        verify(fileStorageService, never()).store(any(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void queuedLocalFileWithNoUrl_stillSignsAndSendsEmailWithoutLink() {
        Offer offer = offer(OfferStatus.SENT);
        StoredFile signedFile = storedFile();
        when(offerAccessService.resolveToken(RAW_TOKEN)).thenReturn(verifiedToken(offer));
        when(pipelineStageRepository.findFirstByPipelineTemplate_IdAndStageTypeAndActiveTrue(
                PIPELINE_TEMPLATE_ID, StageType.TERMINAL_SUCCESS))
                .thenReturn(Optional.of(stage(20L, "Da tuyen", StageType.TERMINAL_SUCCESS)));
        when(offerPdfRenderer.render(any(), any(), anyString(), any(), any())).thenReturn("pdf".getBytes());
        when(fileStorageService.store(any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(signedFile);
        // BR-STORAGE-02: still in the local queue, so no provider URL exists yet.
        when(fileStorageService.getViewUrl(signedFile))
                .thenThrow(new BadRequestException(ErrorCode.FILE_NOT_YET_AVAILABLE));

        offerSigningService.sign(RAW_TOKEN, drawRequest(), CLIENT_IP);

        assertThat(offer.getStatus()).isEqualTo(OfferStatus.SIGNED);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventPublisher).publish(eq(OutboxEventType.OFFER_SIGNED_EMAIL), payload.capture());
        assertThat(payload.getValue().get("signedFileLink")).isEqualTo("");
    }

    private void stubHappyPath(Offer offer, OfferAccessToken token) {
        StoredFile signedFile = storedFile();
        when(offerAccessService.resolveToken(RAW_TOKEN)).thenReturn(token);
        when(pipelineStageRepository.findFirstByPipelineTemplate_IdAndStageTypeAndActiveTrue(
                PIPELINE_TEMPLATE_ID, StageType.TERMINAL_SUCCESS))
                .thenReturn(Optional.of(stage(20L, "Da tuyen", StageType.TERMINAL_SUCCESS)));
        when(offerPdfRenderer.render(any(), any(), anyString(), any(), any())).thenReturn("pdf".getBytes());
        when(fileStorageService.store(any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(signedFile);
        when(fileStorageService.getViewUrl(signedFile)).thenReturn("https://drive.example/signed.pdf");
    }

    private static SignOfferRequestDto drawRequest() {
        return new SignOfferRequestDto(SignatureMethod.DRAW, SIGNATURE_PNG, null);
    }

    private OfferAccessToken verifiedToken(Offer offer) {
        return OfferAccessToken.builder()
                .tokenId(UUID.randomUUID())
                .offer(offer)
                .tokenHash("hash")
                .expiresAt(EXPIRES_AT)
                .otpVerifiedAt(NOW.minusSeconds(60))
                .createdAt(NOW)
                .build();
    }

    private static StoredFile storedFile() {
        return StoredFile.builder()
                .id(99L)
                .fileName("Offer_signed.pdf")
                .mimeType("application/pdf")
                .sizeBytes(3)
                .externalFileId("external-id")
                .status(FileStatus.ACTIVE)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private static PipelineStage stage(Long id, String name, StageType type) {
        PipelineStage stage = new PipelineStage();
        stage.setId(id);
        stage.setName(name);
        stage.setStageType(type);
        return stage;
    }

    private Offer offer(OfferStatus status) {
        Candidate candidate = new Candidate();
        candidate.setId(UUID.randomUUID());
        candidate.setFullName("Nguyen Van A");
        candidate.setPrimaryEmail("nguyenvana@example.com");

        PipelineTemplate template = new PipelineTemplate();
        template.setId(PIPELINE_TEMPLATE_ID);

        JobPosition job = new JobPosition();
        job.setId(UUID.randomUUID());
        job.setTitle("Backend Engineer");
        job.setPipelineTemplate(template);

        Application application = Application.builder()
                .id(APPLICATION_ID)
                .candidate(candidate)
                .jobPosition(job)
                .currentStage(stage(9L, "Offer", StageType.OFFER))
                .status(ApplicationStatus.IN_PROGRESS)
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
