package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.ApplicationStageHistory;
import com.hirewise.be.domain.ApplicationStatus;
import com.hirewise.be.domain.Offer;
import com.hirewise.be.domain.OfferStatus;
import com.hirewise.be.domain.OfferTemplate;
import com.hirewise.be.domain.OfferTemplateStatus;
import com.hirewise.be.domain.PipelineStage;
import com.hirewise.be.domain.StageTransitionType;
import com.hirewise.be.domain.StageType;
import com.hirewise.be.domain.User;
import com.hirewise.be.dto.request.CreateOfferRequestDto;
import com.hirewise.be.dto.response.OfferResponseDto;
import com.hirewise.be.dto.response.OfferTemplateResponseDto;
import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.mapper.OfferMapper;
import com.hirewise.be.repository.ApplicationRepository;
import com.hirewise.be.repository.ApplicationStageHistoryRepository;
import com.hirewise.be.repository.OfferRepository;
import com.hirewise.be.repository.OfferTemplateRepository;
import com.hirewise.be.repository.PipelineStageRepository;
import com.hirewise.be.repository.UserRepository;
import com.hirewise.be.security.CurrentUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * M18 - Offer & e-Signature: UC-36 (generate an Offer Letter from a
 * template, BR-OFFER-01/02). Write-path (Layer 4) ownership is enforced
 * before this service is entered - see {@code @RequiresOwnership} on
 * {@link com.hirewise.be.controller.OfferController#create} and
 * {@link com.hirewise.be.authorization.ApplicationOwnershipResolver} -
 * mirroring {@link ApplicationRejectionService}, so {@link #create}
 * deliberately does not repeat the Layer 2/3/4 check.
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class OfferService {

    /** UC-36 Screen Description field 3: probation rate defaults to 85%. */
    private static final BigDecimal DEFAULT_PROBATION_RATE = new BigDecimal("85.00");

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    ApplicationRepository applicationRepository;
    ApplicationStageHistoryRepository applicationStageHistoryRepository;
    PipelineStageRepository pipelineStageRepository;
    OfferRepository offerRepository;
    OfferTemplateRepository offerTemplateRepository;
    UserRepository userRepository;
    OfferTemplateRenderer offerTemplateRenderer;
    AccessControlService accessControlService;
    Clock clock;

    /**
     * UC-36 step 2: the Offer Templates the Recruiter may pick for this
     * Application - company-wide ones plus those of the job's own department.
     *
     * @param applicationId the Application an Offer is about to be created for
     * @param currentUser   authenticated caller
     * @return selectable active templates
     * @throws ResourceNotFoundException if the Application doesn't exist
     */
    @Transactional(readOnly = true)
    public List<OfferTemplateResponseDto> listTemplates(UUID applicationId, CurrentUser currentUser) {
        Application application = findApplicationOrThrow(applicationId);

        Long departmentId = application.getJobPosition().getDepartment() == null
                ? null
                : application.getJobPosition().getDepartment().getId();
        accessControlService.checkAccess(currentUser, PermissionCodes.OFFER_CREATE,
                ResourceContext.department(departmentId));

        return offerTemplateRepository.findSelectable(OfferTemplateStatus.ACTIVE, departmentId)
                .stream()
                .map(OfferMapper::toTemplateDto)
                .toList();
    }

    /**
     * UC-36 main flow: creates a Draft Offer for an Application - validates
     * the Offer stage precondition and BR-OFFER-01, renders the template body
     * into an immutable snapshot, and saves the offer as {@code DRAFT}
     * (nothing is sent to the candidate until UC-37).
     *
     * @param applicationId id of the Application being offered
     * @param request       template choice plus salary, probation rate, start
     *                      date, answer deadline and - only from the Kanban
     *                      drag path - the Offer stage to move onto first
     * @param currentUser   authenticated caller (already ownership-checked by the controller)
     * @return the Draft offer just created
     * @throws ResourceNotFoundException if the Application, the target stage or the
     *                                    template doesn't exist
     * @throws BadRequestException       if the target stage belongs to another pipeline
     *                                    template (UC-23)
     * @throws BusinessConflictException if the Application already sits in a terminal
     *                                    stage (BR-KANBAN-03), the target stage is
     *                                    inactive, the Application is not at an Offer
     *                                    stage, it already has an active Offer
     *                                    (EX-01, BR-OFFER-01), the template is inactive,
     *                                    or the answer deadline is not before the start date
     */
    @Transactional
    public OfferResponseDto create(UUID applicationId, CreateOfferRequestDto request, CurrentUser currentUser) {
        Application application = findApplicationOrThrow(applicationId);

        // UC-23: dragging a card onto an Offer column must not record the stage
        // change unless the Offer is really created, so the move happens here,
        // inside the same transaction - any validation below rolls it back. Same
        // shape as InterviewService#scheduleInterview does for INTERVIEW columns.
        moveOntoOfferStageIfRequested(application, request.getTargetStageId(), currentUser);

        // TODO: UC-28 - once Scorecards exist, also require a completed
        // Scorecard evaluated as "Dat" before an Offer may be created. The
        // Scorecard module is not implemented yet, so the only enforceable
        // half of UC-36's precondition is the Offer stage below.
        StageType currentStageType = application.getCurrentStage().getStageType();
        if (currentStageType != StageType.OFFER) {
            throw new BusinessConflictException(ErrorCode.APPLICATION_NOT_IN_OFFER_STAGE,
                    application.getCurrentStage().getName());
        }

        // BR-OFFER-01 / EX-01: at most one DRAFT or SENT Offer per Application.
        // The partial unique index is the real guard against a race; this check
        // exists so the Recruiter gets a readable business error instead.
        if (offerRepository.existsByApplication_IdAndStatusIn(applicationId, Offer.ACTIVE_STATUSES)) {
            throw new BusinessConflictException(ErrorCode.OFFER_ALREADY_ACTIVE, OfferStatus.DRAFT.name());
        }

        OfferTemplate template = offerTemplateRepository.findById(request.getOfferTemplateId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.OFFER_TEMPLATE_NOT_FOUND, request.getOfferTemplateId()));
        if (!template.isActive()) {
            throw new BusinessConflictException(ErrorCode.OFFER_TEMPLATE_INACTIVE, template.getId());
        }

        // BR-OFFER-02: a deadline landing after the start date would let the
        // candidate sign for a job that has already begun.
        LocalDate expiryDate = LocalDate.ofInstant(request.getExpiresAt(), BUSINESS_ZONE);
        if (!expiryDate.isBefore(request.getStartDate())) {
            throw new BusinessConflictException(ErrorCode.OFFER_EXPIRY_BEFORE_START_DATE);
        }

        User createdBy = userRepository.getReferenceById(currentUser.userId());
        Instant now = Instant.now(clock);
        Offer offer = Offer.builder()
                .id(UUID.randomUUID())
                .application(application)
                .offerTemplate(template)
                .createdBy(createdBy)
                .salary(request.getSalary())
                .probationRate(request.getProbationRate() == null
                        ? DEFAULT_PROBATION_RATE
                        : request.getProbationRate())
                .startDate(request.getStartDate())
                .expiresAt(request.getExpiresAt())
                .status(OfferStatus.DRAFT)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // UC-36 step 5: freeze the rendered wording now - see Offer#renderedBody.
        offer.setRenderedBody(offerTemplateRenderer.render(template, offer, currentUser.fullName()));
        offer = offerRepository.save(offer);

        log.info("Created offer {} for application {} (template={}, status={})",
                offer.getId(), applicationId, template.getId(), offer.getStatus());

        return OfferMapper.toDto(offer);
    }

    /**
     * Returns one Offer for the Recruiter's review screen (UC-37 step 1).
     *
     * @param offerId id of the offer to read
     * @return the offer, including its rendered body
     * @throws ResourceNotFoundException if no Offer exists with this id
     */
    @Transactional(readOnly = true)
    public OfferResponseDto getById(UUID offerId) {
        return OfferMapper.toDto(findOfferOrThrow(offerId));
    }

    /**
     * The Application's most recent Offer, whatever its status, or
     * {@code null} when it has never been offered - used by the Applicant
     * Card to decide between showing [Tao Offer] and the offer's state.
     *
     * @param applicationId id of the Application
     */
    @Transactional(readOnly = true)
    public OfferResponseDto findLatestForApplication(UUID applicationId) {
        return offerRepository.findFirstByApplication_IdOrderByCreatedAtDesc(applicationId)
                .map(OfferMapper::toDto)
                .orElse(null);
    }

    /**
     * UC-23 half of the Kanban drag onto an Offer column: validates the drop
     * exactly the way {@link KanbanService#moveStage} would, then moves the
     * Application and appends the BR-KANBAN-01 audit row - all still inside
     * {@link #create}'s transaction, so an Offer that fails to be created
     * leaves the Application on its original stage.
     * <p>
     * A {@code null} id, or one naming the stage the Application already sits
     * on, is the Applicant Card button path and does nothing here.
     *
     * @param application   the Application being offered, already loaded
     * @param targetStageId the OFFER-typed stage the card was dropped on, may be {@code null}
     * @param currentUser   authenticated caller, recorded as the author of the move
     */
    private void moveOntoOfferStageIfRequested(
            Application application, Long targetStageId, CurrentUser currentUser) {
        PipelineStage fromStage = application.getCurrentStage();
        if (targetStageId == null || targetStageId.equals(fromStage.getId())) {
            return;
        }

        // BR-KANBAN-03: a terminal Application only leaves its stage through an
        // explicit "Restore" action - not yet built - never a drag.
        if (fromStage.isTerminal()) {
            throw new BusinessConflictException(ErrorCode.APPLICATION_STAGE_TERMINAL, fromStage.getName());
        }

        PipelineStage toStage = pipelineStageRepository.findById(targetStageId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.PIPELINE_STAGE_NOT_FOUND, targetStageId));

        Long pipelineTemplateId = application.getJobPosition().getPipelineTemplate().getId();
        if (!toStage.getPipelineTemplate().getId().equals(pipelineTemplateId)) {
            throw new BadRequestException(ErrorCode.INVALID_STAGE_TRANSITION);
        }
        // BR-KANBAN-03: a soft-deleted stage must never accept a new drop.
        if (!toStage.isActive()) {
            throw new BusinessConflictException(ErrorCode.PIPELINE_STAGE_INACTIVE, toStage.getId());
        }
        if (toStage.getStageType() != StageType.OFFER) {
            throw new BusinessConflictException(ErrorCode.APPLICATION_NOT_IN_OFFER_STAGE, toStage.getName());
        }

        Instant now = Instant.now(clock);
        application.setCurrentStage(toStage);
        // An Offer stage is not terminal, so the same status KanbanService#deriveStatus
        // would pick; OFFER_SENT stays the business of UC-37's send step.
        application.setStatus(ApplicationStatus.IN_PROGRESS);
        application.setLastStageChangedAt(now);
        application.setUpdatedAt(now);
        applicationRepository.save(application);

        // BR-KANBAN-01: append-only audit trail of every stage change.
        applicationStageHistoryRepository.save(ApplicationStageHistory.builder()
                .application(application)
                .fromStage(fromStage)
                .toStage(toStage)
                .changedBy(userRepository.getReferenceById(currentUser.userId()))
                .transitionType(StageTransitionType.MANUAL)
                .changedAt(now)
                .build());

        log.info("Application {} moved stage {} -> {} by user {} while creating an offer",
                application.getId(), fromStage.getId(), toStage.getId(), currentUser.userId());
    }

    private Application findApplicationOrThrow(UUID applicationId) {
        return applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APPLICATION_NOT_FOUND, applicationId));
    }

    private Offer findOfferOrThrow(UUID offerId) {
        return offerRepository.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.OFFER_NOT_FOUND, offerId));
    }
}
