package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.ApplicationStageHistory;
import com.hirewise.be.domain.ApplicationStatus;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.PipelineStage;
import com.hirewise.be.domain.StageTransitionType;
import com.hirewise.be.dto.request.MoveApplicationStageRequestDto;
import com.hirewise.be.dto.response.KanbanBoardResponseDto;
import com.hirewise.be.dto.response.KanbanStageColumnResponseDto;
import com.hirewise.be.dto.response.MoveApplicationStageResponseDto;
import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.mapper.KanbanMapper;
import com.hirewise.be.repository.ApplicationRepository;
import com.hirewise.be.repository.ApplicationStageHistoryRepository;
import com.hirewise.be.repository.JobPositionRepository;
import com.hirewise.be.repository.PipelineStageRepository;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * M11 - Kanban Pipeline: UC-22 (board read) and UC-23 (drag-and-drop stage
 * transition). Write-path (Layer 4) ownership for {@code moveStage} is
 * enforced BEFORE this service is even entered - see {@code @RequiresOwnership}
 * on {@link com.hirewise.be.controller.ApplicationController} and
 * {@link com.hirewise.be.authorization.ApplicationOwnershipResolver} - so
 * {@link #moveStage} deliberately does not repeat the Layer 2/3/4 check.
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class KanbanService {

    JobPositionRepository jobPositionRepository;
    PipelineStageRepository pipelineStageRepository;
    ApplicationRepository applicationRepository;
    ApplicationStageHistoryRepository applicationStageHistoryRepository;
    UserRepository userRepository;
    AccessControlService accessControlService;
    Clock clock;

    /**
     * UC-22 main flow: the Kanban board for one Job - every active Stage of
     * its Pipeline Template (Kanban column order), each carrying the
     * Applications currently sitting in it.
     *
     * @param jobId       id of the job position
     * @param currentUser authenticated caller, must have {@code APPLICATION_VIEW}
     *                    scoped to the job's department
     * @return the board: one column per active stage, most recently
     *         stage-changed application last within each column
     * @throws ResourceNotFoundException if no job exists with {@code jobId}
     * @throws BusinessConflictException if the job has no pipeline configured yet
     */
    public KanbanBoardResponseDto getBoard(UUID jobId, CurrentUser currentUser) {
        JobPosition job = jobPositionRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.JOB_POSITION_NOT_FOUND, jobId));

        Long departmentId = job.getDepartment() != null ? job.getDepartment().getId() : null;
        accessControlService.checkAccess(currentUser, PermissionCodes.APPLICATION_VIEW,
                ResourceContext.job(jobId, departmentId));

        if (job.getPipelineTemplate() == null) {
            // Defensive: UC-13 is supposed to guarantee every job has a pipeline before
            // it can be approved/published, same defensive check as JobApplicationService.
            throw new BusinessConflictException(ErrorCode.PIPELINE_NOT_CONFIGURED, jobId);
        }

        List<PipelineStage> stages = pipelineStageRepository
                .findByPipelineTemplate_IdAndActiveTrueOrderByPositionAsc(job.getPipelineTemplate().getId());
        List<Application> applications = applicationRepository.findByJobPosition_IdFetchCandidateAndStage(jobId);

        Map<Long, List<Application>> applicationsByStageId = applications.stream()
                .collect(Collectors.groupingBy(a -> a.getCurrentStage().getId()));

        List<KanbanStageColumnResponseDto> columns = stages.stream()
                .map(stage -> KanbanMapper.toColumnDto(stage, applicationsByStageId.getOrDefault(stage.getId(), List.of())))
                .toList();

        return KanbanMapper.toBoardDto(job, columns);
    }

    /**
     * UC-23 main flow: drag-and-drop an Application into a different Stage
     * column. Ownership (Layer 4 - caller must be the job's assigned
     * Recruiter) is already enforced by {@code @RequiresOwnership} on the
     * controller before this method runs.
     * <p>
     * BR-KANBAN-01: writes {@code current_stage_id}/{@code last_stage_changed_at}
     * and appends one {@link ApplicationStageHistory} row, in the same
     * transaction. BR-KANBAN-03: rejects a drop into an inactive Stage, a
     * no-op drop onto the current Stage, a target Stage from a different
     * Pipeline Template, and any move away from a Stage that is already
     * terminal (Hired/Refused are final until an explicit "Restore" action
     * - not yet built).
     *
     * @param applicationId id of the Application being moved
     * @param request       target stage id
     * @param currentUser   authenticated caller (already ownership-checked by the controller)
     * @return the application's new stage/status/timestamp
     * @throws ResourceNotFoundException if the application or the target stage doesn't exist
     * @throws BusinessConflictException if the application is already in a terminal stage
     *                                    (BR-KANBAN-03), or the target stage is inactive
     * @throws BadRequestException       if the target stage belongs to a different pipeline
     *                                    template, or is the application's current stage
     */
    @Transactional
    public MoveApplicationStageResponseDto moveStage(
            UUID applicationId, MoveApplicationStageRequestDto request, CurrentUser currentUser) {
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APPLICATION_NOT_FOUND, applicationId));

        PipelineStage fromStage = application.getCurrentStage();
        // BR-KANBAN-03: once in a terminal stage (Hired/Refused), an Application only
        // leaves it through an explicit "Restore" action - not yet built - never a plain drag.
        if (fromStage.isTerminal()) {
            throw new BusinessConflictException(ErrorCode.APPLICATION_STAGE_TERMINAL, fromStage.getName());
        }

        PipelineStage toStage = pipelineStageRepository.findById(request.getTargetStageId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        ErrorCode.PIPELINE_STAGE_NOT_FOUND, request.getTargetStageId()));

        Long pipelineTemplateId = application.getJobPosition().getPipelineTemplate().getId();
        if (!toStage.getPipelineTemplate().getId().equals(pipelineTemplateId)
                || toStage.getId().equals(fromStage.getId())) {
            throw new BadRequestException(ErrorCode.INVALID_STAGE_TRANSITION);
        }
        // BR-KANBAN-03: a soft-deleted (UC-06) stage must never accept a new drop.
        if (!toStage.isActive()) {
            throw new BusinessConflictException(ErrorCode.PIPELINE_STAGE_INACTIVE, toStage.getId());
        }

        Instant now = Instant.now(clock);
        application.setCurrentStage(toStage);
        application.setLastStageChangedAt(now);
        application.setStatus(deriveStatus(toStage));
        application.setUpdatedAt(now);
        applicationRepository.save(application);

        // BR-KANBAN-01: append-only audit trail of every stage change.
        ApplicationStageHistory history = ApplicationStageHistory.builder()
                .application(application)
                .fromStage(fromStage)
                .toStage(toStage)
                .changedBy(userRepository.getReferenceById(currentUser.userId()))
                .transitionType(StageTransitionType.MANUAL)
                .changedAt(now)
                .build();
        applicationStageHistoryRepository.save(history);

        log.info("Application {} moved stage {} -> {} by user {}",
                applicationId, fromStage.getId(), toStage.getId(), currentUser.userId());

        return KanbanMapper.toMoveResponseDto(application, fromStage.getId());
    }

    /** Best-effort {@link ApplicationStatus} derived from the Kanban column an Application lands in. */
    private static ApplicationStatus deriveStatus(PipelineStage stage) {
        return switch (stage.getStageType()) {
            case TERMINAL_SUCCESS -> ApplicationStatus.HIRED;
            case TERMINAL_REJECTED -> ApplicationStatus.REFUSED;
            default -> ApplicationStatus.IN_PROGRESS;
        };
    }
}
