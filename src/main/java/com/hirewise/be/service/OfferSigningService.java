package com.hirewise.be.service;

import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.ApplicationStageHistory;
import com.hirewise.be.domain.ApplicationStatus;
import com.hirewise.be.domain.Offer;
import com.hirewise.be.domain.OfferFile;
import com.hirewise.be.domain.OfferFileRole;
import com.hirewise.be.domain.OfferSignature;
import com.hirewise.be.domain.OfferStatus;
import com.hirewise.be.domain.PipelineStage;
import com.hirewise.be.domain.SignatureMethod;
import com.hirewise.be.domain.StageTransitionType;
import com.hirewise.be.domain.StageType;
import com.hirewise.be.domain.StoredFile;
import com.hirewise.be.dto.request.SignOfferRequestDto;
import com.hirewise.be.dto.response.PublicOfferContentDto;
import com.hirewise.be.event.OutboxEventPublisher;
import com.hirewise.be.event.OutboxEventType;
import com.hirewise.be.event.OutboxPayloads;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

/**
 * M18 - Offer & e-Signature: UC-39 (the candidate signs, BR-OFFER-04).
 * <p>
 * Like {@link OfferAccessService} this sits outside RBAC - see that class
 * and {@link com.hirewise.be.controller.PublicOfferController} for why - and
 * reuses its token/OTP validation rather than re-implementing it.
 * <p>
 * Everything the SRS lists as a postcondition happens in ONE transaction:
 * the signature evidence, the offer's new status, the Application's move to
 * Hired with its history row, retiring the link, and enqueueing EM-12. A
 * candidate must never end up signed-but-not-hired, or hired without an
 * evidence record.
 */
@Slf4j
@Service
public class OfferSigningService {

    private static final DateTimeFormatter VI_DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));
    private static final DateTimeFormatter VI_DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String PNG_DATA_URI_PREFIX = "data:image/png;base64,";

    private final OfferAccessService offerAccessService;
    private final OfferRepository offerRepository;
    private final OfferAccessTokenRepository offerAccessTokenRepository;
    private final OfferSignatureRepository offerSignatureRepository;
    private final OfferFileRepository offerFileRepository;
    private final ApplicationRepository applicationRepository;
    private final ApplicationStageHistoryRepository applicationStageHistoryRepository;
    private final PipelineStageRepository pipelineStageRepository;
    private final OfferPdfRenderer offerPdfRenderer;
    private final FileStorageService fileStorageService;
    private final OutboxEventPublisher outboxEventPublisher;
    private final Clock clock;
    private final String productName;

    public OfferSigningService(OfferAccessService offerAccessService,
                                OfferRepository offerRepository,
                                OfferAccessTokenRepository offerAccessTokenRepository,
                                OfferSignatureRepository offerSignatureRepository,
                                OfferFileRepository offerFileRepository,
                                ApplicationRepository applicationRepository,
                                ApplicationStageHistoryRepository applicationStageHistoryRepository,
                                PipelineStageRepository pipelineStageRepository,
                                OfferPdfRenderer offerPdfRenderer,
                                FileStorageService fileStorageService,
                                OutboxEventPublisher outboxEventPublisher,
                                Clock clock,
                                @Value("${app.mail.product-name:HireWise}") String productName) {
        this.offerAccessService = offerAccessService;
        this.offerRepository = offerRepository;
        this.offerAccessTokenRepository = offerAccessTokenRepository;
        this.offerSignatureRepository = offerSignatureRepository;
        this.offerFileRepository = offerFileRepository;
        this.applicationRepository = applicationRepository;
        this.applicationStageHistoryRepository = applicationStageHistoryRepository;
        this.pipelineStageRepository = pipelineStageRepository;
        this.offerPdfRenderer = offerPdfRenderer;
        this.fileStorageService = fileStorageService;
        this.outboxEventPublisher = outboxEventPublisher;
        this.clock = clock;
        this.productName = productName;
    }

    /**
     * UC-39 main flow: records the signature, produces and stores the signed
     * PDF, locks the Offer, moves the Application to Hired and enqueues EM-12.
     *
     * @param rawToken the raw link token from the URL
     * @param request  signing method plus the drawn image or typed name
     * @param clientIp the signer's IP, kept as signature evidence
     * @return the offer's terms in their new Signed state
     * @throws com.hirewise.be.exception.InvalidTokenException if the link cannot be resolved
     * @throws BadRequestException       if no OTP was verified recently (BR-OFFER-03),
     *                                    or the signature is blank/unreadable (EX-01, ME-34)
     * @throws BusinessConflictException if the deadline passed (ME-32), the offer is
     *                                    already signed (BR-OFFER-04), or the pipeline has
     *                                    no Terminal-Success stage
     */
    @Transactional
    public PublicOfferContentDto sign(String rawToken, SignOfferRequestDto request, String clientIp) {
        OfferAccessToken token = offerAccessService.resolveToken(rawToken);
        Offer offer = token.getOffer();
        Instant now = Instant.now(clock);

        offerAccessService.expireIfPastDeadline(offer, now);
        offerAccessService.requireVerifiedOtp(token, now);

        // BR-OFFER-04: signed content is immutable - a second signature would
        // mean two different "originals" of the same contract.
        if (offer.getStatus() == OfferStatus.SIGNED) {
            throw new BusinessConflictException(ErrorCode.OFFER_ALREADY_SIGNED);
        }
        if (offer.getStatus() != OfferStatus.SENT) {
            throw new BusinessConflictException(ErrorCode.OFFER_NOT_SIGNABLE, offer.getStatus().name());
        }

        String signerName = resolveSignerName(offer, request);
        String signatureImageDataUri = resolveSignatureImage(request);

        Application application = offer.getApplication();
        // Resolve the destination stage BEFORE generating the PDF: a pipeline
        // missing its Terminal-Success stage is a misconfiguration, and finding
        // out after the upload would leave an orphaned file on Cloud Storage.
        PipelineStage toStage = resolveHiredStage(application);

        log.info("Rendering signed PDF for offer {} (method={})", offer.getId(), request.getMethod());
        byte[] pdf = offerPdfRenderer.render(offer, request.getMethod(), signerName, signatureImageDataUri, now);

        // Never throws for a Cloud Storage-side problem - BR-STORAGE-02 queues
        // the file locally instead, so an outage cannot block a candidate from
        // accepting a job.
        StoredFile signedFile = fileStorageService.store(pdf,
                "Offer_" + offer.getId() + "_signed.pdf",
                "application/pdf",
                "offers",
                "Offer_" + offer.getId() + "_signed.pdf");
        log.info("Stored signed offer PDF {} for offer {}", signedFile.getId(), offer.getId());

        offerFileRepository.save(OfferFile.builder()
                .offer(offer)
                .file(signedFile)
                .fileRole(OfferFileRole.OFFER_SIGNED)
                .createdAt(now)
                .build());

        offerSignatureRepository.save(OfferSignature.builder()
                .offer(offer)
                .signerCandidate(application.getCandidate())
                .signedFile(signedFile)
                .method(request.getMethod())
                .signerName(signerName)
                // Copied off the token: that row is operational state, this is evidence.
                .otpVerifiedAt(token.getOtpVerifiedAt())
                .signedAt(now)
                .ipAddress(clientIp)
                .build());

        offer.setStatus(OfferStatus.SIGNED);
        offer.setSignedAt(now);
        offer.setUpdatedAt(now);
        offerRepository.save(offer);

        moveApplicationToHired(application, toStage, now);

        // The link has done its job; retiring it stops the signed contract page
        // from staying reachable to anyone who later gets hold of the email.
        token.setUsedAt(now);
        offerAccessTokenRepository.save(token);

        outboxEventPublisher.publish(OutboxEventType.OFFER_SIGNED_EMAIL,
                OutboxPayloads.offerSignedEmail(
                        application.getCandidate().getPrimaryEmail(),
                        application.getCandidate().getFullName(),
                        application.getJobPosition().getTitle(),
                        VI_DATE_TIME_FORMATTER.format(now),
                        offer.getStartDate().format(VI_DATE_FORMATTER),
                        signedFileLinkOrBlank(signedFile)));

        log.info("Offer {} signed (method={}); application {} moved to Hired",
                offer.getId(), request.getMethod(), application.getId());

        return PublicOfferContentDto.builder()
                .jobTitle(application.getJobPosition().getTitle())
                .companyName(productName)
                .candidateName(application.getCandidate().getFullName())
                .salary(offer.getSalary())
                .probationRate(offer.getProbationRate())
                .startDate(offer.getStartDate())
                .expiresAt(offer.getExpiresAt())
                .status(offer.getStatus().name())
                .renderedBody(offer.getRenderedBody())
                .signed(true)
                .signedAt(now)
                .build();
    }

    /**
     * EX-01 / ME-34: the signature must actually contain something. Which
     * field carries it depends on the method (LV-22), which is why this is a
     * business check rather than a bean-validation annotation.
     */
    private String resolveSignerName(Offer offer, SignOfferRequestDto request) {
        if (request.getMethod() == SignatureMethod.TYPE) {
            String typedName = request.getTypedName();
            if (typedName == null || typedName.isBlank()) {
                throw new BadRequestException(ErrorCode.OFFER_SIGNATURE_REQUIRED);
            }
            return typedName.trim();
        }
        // A drawn signature is an image, so the printed name under it comes
        // from the candidate record rather than from the request.
        return offer.getApplication().getCandidate().getFullName();
    }

    /** @return the PNG data URI for a drawn signature, or {@code null} for a typed one */
    private String resolveSignatureImage(SignOfferRequestDto request) {
        if (request.getMethod() != SignatureMethod.DRAW) {
            return null;
        }
        String image = request.getSignatureImageBase64();
        if (image == null || image.isBlank()) {
            throw new BadRequestException(ErrorCode.OFFER_SIGNATURE_REQUIRED);
        }
        // Accept the canvas' toDataURL() output as-is, or bare base64.
        String base64 = image.startsWith(PNG_DATA_URI_PREFIX)
                ? image.substring(PNG_DATA_URI_PREFIX.length())
                : image;
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(ErrorCode.OFFER_SIGNATURE_IMAGE_INVALID);
        }
        // An empty canvas still serialises to a valid PNG, so size is the only
        // signal here that the candidate never actually drew anything.
        if (decoded.length == 0) {
            throw new BadRequestException(ErrorCode.OFFER_SIGNATURE_REQUIRED);
        }
        return PNG_DATA_URI_PREFIX + base64;
    }

    /**
     * BR-PIPE-01 guarantees this stage exists on every pipeline; the guard is
     * defensive only - same shape as
     * {@code ApplicationRejectionService}'s Terminal-Rejected lookup.
     */
    private PipelineStage resolveHiredStage(Application application) {
        Long pipelineTemplateId = application.getJobPosition().getPipelineTemplate().getId();
        return pipelineStageRepository
                .findFirstByPipelineTemplate_IdAndStageTypeAndActiveTrue(pipelineTemplateId, StageType.TERMINAL_SUCCESS)
                .orElseThrow(() -> new BusinessConflictException(
                        ErrorCode.PIPELINE_MISSING_TERMINAL_SUCCESS_STAGE, pipelineTemplateId));
    }

    /**
     * UC-39 step 6: the move is automatic, so the history row is
     * {@code SYSTEM} with no {@code changedBy} - nobody performed it.
     */
    private void moveApplicationToHired(Application application, PipelineStage toStage, Instant now) {
        PipelineStage fromStage = application.getCurrentStage();
        application.setCurrentStage(toStage);
        application.setStatus(ApplicationStatus.HIRED);
        application.setLastStageChangedAt(now);
        application.setUpdatedAt(now);
        applicationRepository.save(application);

        // BR-KANBAN-01: append-only trail of every stage change, automatic ones included.
        applicationStageHistoryRepository.save(ApplicationStageHistory.builder()
                .application(application)
                .fromStage(fromStage)
                .toStage(toStage)
                .changedBy(null)
                .transitionType(StageTransitionType.SYSTEM)
                .changedAt(now)
                .build());
    }

    /**
     * BR-STORAGE-02: a file still sitting in the local pending-upload queue
     * has no provider URL yet, so EM-12 says the contract will follow instead
     * of carrying a dead link.
     */
    private String signedFileLinkOrBlank(StoredFile signedFile) {
        try {
            return fileStorageService.getViewUrl(signedFile);
        } catch (RuntimeException e) {
            log.warn("Signed offer PDF {} has no view URL yet: {}", signedFile.getId(), e.getMessage());
            return "";
        }
    }
}
