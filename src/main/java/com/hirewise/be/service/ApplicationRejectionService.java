package com.hirewise.be.service;

import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.ApplicationRejection;
import com.hirewise.be.domain.ApplicationStageHistory;
import com.hirewise.be.domain.ApplicationStatus;
import com.hirewise.be.domain.PipelineStage;
import com.hirewise.be.domain.RejectionReason;
import com.hirewise.be.domain.StageTransitionType;
import com.hirewise.be.domain.StageType;
import com.hirewise.be.dto.request.RejectApplicationRequestDto;
import com.hirewise.be.dto.response.ApplicationRejectionResponseDto;
import com.hirewise.be.event.OutboxEventPublisher;
import com.hirewise.be.event.OutboxEventType;
import com.hirewise.be.event.OutboxPayloads;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.mapper.ApplicationMapper;
import com.hirewise.be.repository.ApplicationRejectionRepository;
import com.hirewise.be.repository.ApplicationRepository;
import com.hirewise.be.repository.ApplicationStageHistoryRepository;
import com.hirewise.be.repository.PipelineStageRepository;
import com.hirewise.be.repository.RejectionReasonRepository;
import com.hirewise.be.repository.UserRepository;
import com.hirewise.be.security.CurrentUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * M14 - Candidate Rejection: UC-29 (reject with standardized reason,
 * BR-REJ-01) and the trigger for UC-30 (automatic rejection email,
 * BR-REJ-02). Write-path (Layer 4) ownership is already enforced before
 * this service is entered - see {@code @RequiresOwnership} on
 * {@link com.hirewise.be.controller.ApplicationController#reject} and
 * {@link com.hirewise.be.authorization.ApplicationOwnershipResolver} -
 * mirroring {@link KanbanService#moveStage}, so {@link #reject} deliberately
 * does not repeat the Layer 2/3/4 check.
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class ApplicationRejectionService {

    ApplicationRepository applicationRepository;
    ApplicationStageHistoryRepository applicationStageHistoryRepository;
    ApplicationRejectionRepository applicationRejectionRepository;
    RejectionReasonRepository rejectionReasonRepository;
    PipelineStageRepository pipelineStageRepository;
    UserRepository userRepository;
    OutboxEventPublisher outboxEventPublisher;
    Clock clock;

    /**
     * UC-29 main flow: rejects an Application - validates the standardized
     * reason (BR-REJ-01), moves it to the Pipeline's Terminal-Rejected
     * stage (append-only history, same as {@link KanbanService#moveStage}),
     * records the rejection, and enqueues the automatic rejection email
     * (BR-REJ-02, UC-30).
     *
     * @param applicationId id of the Application being rejected
     * @param request       standardized reason id + optional custom message (BR-REJ-01)
     * @param currentUser   authenticated caller (already ownership-checked by the controller)
     * @return the rejection record just created
     * @throws ResourceNotFoundException if the application or the reason doesn't exist
     * @throws BusinessConflictException if the application is already in a terminal stage
     *                                    (BR-REJ-03), the reason is inactive, or the pipeline
     *                                    has no configured Terminal-Rejected stage
     */
    @Transactional
    public ApplicationRejectionResponseDto reject(
            UUID applicationId, RejectApplicationRequestDto request, CurrentUser currentUser) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APPLICATION_NOT_FOUND, applicationId));

        PipelineStage fromStage = application.getCurrentStage();
        // BR-REJ-03: Refused is terminal - no revert, a new Application must be created to reconsider.
        if (fromStage.isTerminal()) {
            throw new BusinessConflictException(ErrorCode.APPLICATION_STAGE_TERMINAL, fromStage.getName());
        }

        RejectionReason reason = rejectionReasonRepository.findById(request.getReasonId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.REJECTION_REASON_NOT_FOUND, request.getReasonId()));
        // BR-REJ-01: the standardized reason picked must be one still offered to Recruiters.
        if (!reason.isActive()) {
            throw new BusinessConflictException(ErrorCode.REJECTION_REASON_INACTIVE, reason.getCode());
        }

        Long pipelineTemplateId = application.getJobPosition().getPipelineTemplate().getId();
        // BR-PIPE-01 guarantees this stage exists on every pipeline; this guard is defensive only.
        PipelineStage toStage = pipelineStageRepository
                .findFirstByPipelineTemplate_IdAndStageTypeAndActiveTrue(pipelineTemplateId, StageType.TERMINAL_REJECTED)
                .orElseThrow(() -> new BusinessConflictException(
                        ErrorCode.PIPELINE_MISSING_TERMINAL_REJECTED_STAGE, pipelineTemplateId));

        Instant now = Instant.now(clock);
        application.setCurrentStage(toStage);
        application.setStatus(ApplicationStatus.REFUSED);
        application.setLastStageChangedAt(now);
        application.setUpdatedAt(now);
        applicationRepository.save(application);

        // BR-KANBAN-01: append-only audit trail of every stage change, rejection included.
        ApplicationStageHistory history = ApplicationStageHistory.builder()
                .application(application)
                .fromStage(fromStage)
                .toStage(toStage)
                .changedBy(userRepository.getReferenceById(currentUser.userId()))
                .transitionType(StageTransitionType.MANUAL)
                .changedAt(now)
                .build();
        applicationStageHistoryRepository.save(history);

        // BR-REJ-01/03: one rejection record per Application (unique on application_id).
        ApplicationRejection rejection = ApplicationRejection.builder()
                .application(application)
                .reason(reason)
                .rejectedBy(userRepository.getReferenceById(currentUser.userId()))
                .customMessage(request.getCustomMessage())
                .rejectedAt(now)
                .build();
        rejection = applicationRejectionRepository.save(rejection);

        // BR-REJ-02 / UC-30: enqueue the automatic rejection email in the same transaction
        // as the business change - see OutboxEventPublisher for why this beats sending synchronously.
        outboxEventPublisher.publish(OutboxEventType.APPLICATION_REJECTION_EMAIL,
                OutboxPayloads.applicationRejectionEmail(
                        application.getCandidate().getPrimaryEmail(),
                        application.getCandidate().getFullName(),
                        application.getJobPosition().getTitle(),
                        reason.getLabel(),
                        request.getCustomMessage()));

        log.info("Application {} rejected (reason={}) by user {}", applicationId, reason.getCode(), currentUser.userId());

        return ApplicationMapper.toRejectionDto(rejection);
    }
}
