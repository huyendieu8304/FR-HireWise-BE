package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.Department;
import com.hirewise.be.domain.PipelineStage;
import com.hirewise.be.domain.PipelineTemplate;
import com.hirewise.be.domain.PipelineTemplateStatus;
import com.hirewise.be.domain.StageType;
import com.hirewise.be.dto.request.CreatePipelineStageRequestDto;
import com.hirewise.be.dto.request.CreatePipelineTemplateRequestDto;
import com.hirewise.be.dto.request.ReorderPipelineStagesRequestDto;
import com.hirewise.be.dto.response.PipelineStageResponseDto;
import com.hirewise.be.dto.response.PipelineTemplateResponseDto;
import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.mapper.PipelineMapper;
import com.hirewise.be.repository.ApplicationRepository;
import com.hirewise.be.repository.DepartmentRepository;
import com.hirewise.be.repository.PipelineStageRepository;
import com.hirewise.be.repository.PipelineTemplateRepository;
import com.hirewise.be.security.CurrentUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * UC-04/UC-05/UC-06: HR Admin configuration of the recruitment Pipeline
 * (Pipeline Template + Stage) - creating stages (UC-04), reordering them
 * (UC-05), and deleting/soft-deleting a stage (UC-06).
 * <p>
 * A new template always starts in {@link PipelineTemplateStatus#DRAFT} -
 * BR-PIPE-01 (at least 2 stages, including one {@code TERMINAL_SUCCESS}
 * and one {@code TERMINAL_REJECTED}) is a precondition for moving a
 * template to {@code ACTIVE}, which is a different action (not triggered
 * by this use case's normal flow) and is therefore not implemented here
 * either.
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class PipelineService {

    PipelineTemplateRepository pipelineTemplateRepository;
    PipelineStageRepository pipelineStageRepository;
    DepartmentRepository departmentRepository;
    ApplicationRepository applicationRepository;
    AccessControlService accessControlService;
    Clock clock;

    /**
     * UC-04 step 1: pipeline templates an HR Admin can choose from (or add
     * a new stage to).
     *
     * @param currentUser authenticated caller, must have {@code PIPELINE_MANAGE}
     * @return every template, most recently created first
     */
    public List<PipelineTemplateResponseDto> listTemplates(CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.PIPELINE_MANAGE, ResourceContext.none());
        return pipelineTemplateRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(PipelineMapper::toResponseDto)
                .toList();
    }

    /**
     * UC-04 AF-01: creates a new Pipeline Template, in {@code DRAFT}
     * status, with no stages yet.
     *
     * @param request     template name and optional owning department
     * @param currentUser HR Admin performing the creation
     * @return the created template
     * @throws ResourceNotFoundException if {@code departmentId} is set but does not exist
     */
    @Transactional
    public PipelineTemplateResponseDto createTemplate(
            CreatePipelineTemplateRequestDto request, CurrentUser currentUser) {
        // Layer 3 needs the target department up front for a create action (there is no
        // existing row yet to load it from) - null means company-wide (see AccessScopeService,
        // which only lets a SYSTEM-scope user act on a null-department resource).
        accessControlService.checkAccess(currentUser, PermissionCodes.PIPELINE_MANAGE,
                ResourceContext.department(request.getDepartmentId()));

        Department department = resolveDepartmentOrNull(request.getDepartmentId());
        Instant now = Instant.now(clock);

        PipelineTemplate template = PipelineTemplate.builder()
                .name(request.getName())
                .department(department)
                .status(PipelineTemplateStatus.DRAFT)
                .createdAt(now)
                .updatedAt(now)
                .build();
        pipelineTemplateRepository.save(template);

        log.info("Created pipeline template: {} (name={}, departmentId={})",
                template.getId(), template.getName(), request.getDepartmentId());
        return PipelineMapper.toResponseDto(template);
    }

    /**
     * UC-04 step 1: current stages of a template, in Kanban column order.
     * Stages soft-deleted by UC-06 ({@code is_active = false}) are excluded.
     *
     * @param templateId  id of the pipeline template
     * @param currentUser authenticated caller, must have {@code PIPELINE_MANAGE}
     * @return the template's active stages ordered by position
     * @throws ResourceNotFoundException if no template exists with {@code templateId}
     */
    public List<PipelineStageResponseDto> listStages(Long templateId, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.PIPELINE_MANAGE, ResourceContext.none());
        findTemplateOrThrow(templateId);

        List<PipelineStage> stages =
                pipelineStageRepository.findByPipelineTemplate_IdAndActiveTrueOrderByPositionAsc(templateId);
        return toStageResponseDtos(stages);
    }

    /**
     * UC-04 main flow steps 2-5: appends a new Stage to a Pipeline
     * Template. The new stage is always inserted at the end (BR-PIPE-04) -
     * reordering existing stages afterward is UC-05, a separate endpoint.
     *
     * @param templateId  id of the pipeline template to add the stage to
     * @param request     new stage's name/code/type/terminal flag/SLA
     * @param currentUser HR Admin performing the creation
     * @return the created stage
     * @throws ResourceNotFoundException if no template exists with {@code templateId}
     * @throws BusinessConflictException if {@code request.code} is already used by
     *                                    another stage in the same template (EX-01)
     */
    @Transactional
    public PipelineStageResponseDto createStage(
            Long templateId, CreatePipelineStageRequestDto request, CurrentUser currentUser) {
        // The template must be loaded first to know which department scope (Layer 3) the
        // new stage inherits - a stage has no department of its own (see AccessControlService
        // Javadoc: "resource usually needs to be loaded first to determine which scope it belongs to").
        PipelineTemplate template = findTemplateOrThrow(templateId);
        Long departmentId = template.getDepartment() != null ? template.getDepartment().getId() : null;
        accessControlService.checkAccess(currentUser, PermissionCodes.PIPELINE_MANAGE,
                ResourceContext.department(departmentId));

        if (pipelineStageRepository.existsByPipelineTemplate_IdAndCode(templateId, request.getCode())) {
            throw new BusinessConflictException(ErrorCode.PIPELINE_STAGE_CODE_ALREADY_EXISTS, request.getCode());
        }

        // StageType Javadoc: a TERMINAL_SUCCESS/TERMINAL_REJECTED stage is always terminal,
        // regardless of what the "Is Terminal" checkbox was set to on the request.
        boolean terminal = request.isTerminal() || isTerminalStageType(request.getStageType());
        int nextPosition = pipelineStageRepository.findMaxPosition(templateId) + 1;
        Instant now = Instant.now(clock);

        PipelineStage stage = PipelineStage.builder()
                .pipelineTemplate(template)
                .name(request.getName())
                .code(request.getCode())
                .stageType(request.getStageType())
                .position(nextPosition)
                .terminal(terminal)
                .slaHours(request.getSlaHours())
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();
        pipelineStageRepository.save(stage);

        log.info("Created pipeline stage: {} (templateId={}, code={}, position={})",
                stage.getId(), templateId, stage.getCode(), stage.getPosition());
        // A brand-new stage can't have any Application pointing at it yet - no need to query.
        return PipelineMapper.toResponseDto(stage, 0L);
    }

    /**
     * UC-05 main flow: reorders every Stage of a Pipeline Template in one
     * shot. {@code request.stageIds} must be the exact, full set of stage
     * ids currently in the template, listed in the desired new order -
     * {@code position = index + 1} is assigned for each and all rows are
     * saved together in one transaction (BR-PIPE-04), matching the
     * "toàn bộ position ... trong cùng 1 transaction" requirement so a
     * partial/failed save (EX-01) can never leave the template with a
     * gap or a duplicate position.
     *
     * @param templateId  id of the pipeline template whose stages are being reordered
     * @param request     the full list of stage ids in the desired new order
     * @param currentUser HR Admin performing the reorder
     * @return the template's stages in their new order
     * @throws ResourceNotFoundException if no template exists with {@code templateId}
     * @throws BadRequestException       if {@code request.stageIds} is not exactly the
     *                                    same set of ids as the template's current stages
     *                                    (missing id, unknown id, or duplicate)
     */
    @Transactional
    public List<PipelineStageResponseDto> reorderStages(
            Long templateId, ReorderPipelineStagesRequestDto request, CurrentUser currentUser) {
        PipelineTemplate template = findTemplateOrThrow(templateId);
        Long departmentId = template.getDepartment() != null ? template.getDepartment().getId() : null;
        accessControlService.checkAccess(currentUser, PermissionCodes.PIPELINE_MANAGE,
                ResourceContext.department(departmentId));

        // Soft-deleted (UC-06) stages must never be reorderable - only ever offer the active set.
        List<PipelineStage> currentStages =
                pipelineStageRepository.findByPipelineTemplate_IdAndActiveTrueOrderByPositionAsc(templateId);
        Map<Long, PipelineStage> stageById =
                currentStages.stream().collect(Collectors.toMap(PipelineStage::getId, Function.identity()));
        validateReorderSet(request.getStageIds(), stageById.keySet());

        Instant now = Instant.now(clock);
        List<Long> orderedIds = request.getStageIds();
        for (int index = 0; index < orderedIds.size(); index++) {
            PipelineStage stage = stageById.get(orderedIds.get(index));
            stage.setPosition(index + 1);
            stage.setUpdatedAt(now);
        }
        pipelineStageRepository.saveAll(currentStages);

        log.info("Reordered {} pipeline stages for templateId={}", currentStages.size(), templateId);
        List<PipelineStage> reorderedStages = orderedIds.stream().map(stageById::get).toList();
        return toStageResponseDtos(reorderedStages);
    }

    /**
     * UC-06 main flow: soft-deletes a Stage (sets {@code is_active = false})
     * and re-indexes the remaining active stages so {@code position} stays
     * a contiguous ascending sequence (BR-PIPE-04). A hard delete is never
     * used - the row must survive to keep {@code application_stage_history}
     * intact even after the stage itself is retired (SRS "Other Information").
     *
     * @param templateId  id of the pipeline template the stage belongs to
     * @param stageId     id of the stage to delete
     * @param currentUser HR Admin performing the deletion
     * @throws ResourceNotFoundException if no template exists with {@code templateId}, or no
     *                                    stage with {@code stageId} exists within that template
     * @throws BusinessConflictException if at least one Application currently has
     *                                    {@code current_stage_id} pointing at this stage (EX-01, BR-PIPE-03)
     */
    @Transactional
    public void deleteStage(Long templateId, Long stageId, CurrentUser currentUser) {
        // Same reasoning as createStage/reorderStages: the stage has no department of its
        // own, so the parent template must be loaded first to know the Layer 3 scope.
        PipelineTemplate template = findTemplateOrThrow(templateId);
        Long departmentId = template.getDepartment() != null ? template.getDepartment().getId() : null;
        accessControlService.checkAccess(currentUser, PermissionCodes.PIPELINE_MANAGE,
                ResourceContext.department(departmentId));

        // Also require isActive() - an already soft-deleted stage must behave as "not found"
        // for every operation (listStages already excludes it), including a second delete
        // attempt, instead of silently "succeeding" again with a no-op re-deactivation.
        PipelineStage stage = pipelineStageRepository.findById(stageId)
                .filter(s -> s.getPipelineTemplate().getId().equals(templateId) && s.isActive())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PIPELINE_STAGE_NOT_FOUND, stageId));

        // BR-PIPE-03/EX-01: block the delete outright if any Application still references this
        // stage - checked BEFORE touching any row, so a rejected delete never partially applies.
        long applicationCount = applicationRepository.countByCurrentStage_Id(stageId);
        if (applicationCount > 0) {
            throw new BusinessConflictException(ErrorCode.PIPELINE_STAGE_HAS_APPLICATIONS, applicationCount);
        }

        Instant now = Instant.now(clock);
        stage.setActive(false);
        stage.setUpdatedAt(now);
        pipelineStageRepository.save(stage);

        // BR-PIPE-04: closing the gap left behind - re-number the remaining active stages
        // 1..N so position stays contiguous, in the SAME transaction as the soft-delete.
        List<PipelineStage> remaining =
                pipelineStageRepository.findByPipelineTemplate_IdAndActiveTrueOrderByPositionAsc(templateId);
        for (int index = 0; index < remaining.size(); index++) {
            remaining.get(index).setPosition(index + 1);
            remaining.get(index).setUpdatedAt(now);
        }
        pipelineStageRepository.saveAll(remaining);

        log.info("Soft-deleted pipeline stage: {} (templateId={}), re-indexed {} remaining stages",
                stageId, templateId, remaining.size());
    }

    /**
     * Activates a Pipeline Template ({@code DRAFT} -> {@code ACTIVE}) once
     * it satisfies BR-PIPE-01 - a prerequisite added for UC-13 ("Đã có ít
     * nhất 1 Pipeline Template Active" precondition), since nothing else in
     * the system could ever move a template out of {@code DRAFT} otherwise.
     * Idempotent - activating an already-{@code ACTIVE} template is a no-op,
     * not an error.
     *
     * @param templateId  id of the pipeline template to activate
     * @param currentUser HR Admin performing the activation
     * @return the activated template
     * @throws ResourceNotFoundException if no template exists with {@code templateId}
     * @throws BusinessConflictException if the template does not yet have >= 2 active
     *                                    stages including one {@code TERMINAL_SUCCESS} and
     *                                    one {@code TERMINAL_REJECTED} stage (BR-PIPE-01/ME-11)
     */
    @Transactional
    public PipelineTemplateResponseDto activateTemplate(Long templateId, CurrentUser currentUser) {
        PipelineTemplate template = findTemplateOrThrow(templateId);
        Long departmentId = template.getDepartment() != null ? template.getDepartment().getId() : null;
        accessControlService.checkAccess(currentUser, PermissionCodes.PIPELINE_MANAGE,
                ResourceContext.department(departmentId));

        if (template.getStatus() == PipelineTemplateStatus.ACTIVE) {
            return PipelineMapper.toResponseDto(template);
        }

        List<PipelineStage> activeStages =
                pipelineStageRepository.findByPipelineTemplate_IdAndActiveTrueOrderByPositionAsc(templateId);
        boolean hasEnoughStages = activeStages.size() >= 2;
        boolean hasTerminalSuccess = activeStages.stream()
                .anyMatch(stage -> stage.getStageType() == StageType.TERMINAL_SUCCESS);
        boolean hasTerminalRejected = activeStages.stream()
                .anyMatch(stage -> stage.getStageType() == StageType.TERMINAL_REJECTED);
        if (!hasEnoughStages || !hasTerminalSuccess || !hasTerminalRejected) {
            throw new BusinessConflictException(ErrorCode.PIPELINE_TEMPLATE_NOT_READY_TO_ACTIVATE);
        }

        template.setStatus(PipelineTemplateStatus.ACTIVE);
        template.setUpdatedAt(Instant.now(clock));
        pipelineTemplateRepository.save(template);

        log.info("Activated pipeline template: {} ({} active stages)", templateId, activeStages.size());
        return PipelineMapper.toResponseDto(template);
    }

    /**
     * Maps stages to response DTOs, resolving each one's current
     * {@code applicationCount} (BR-PIPE-03) along the way - kept here
     * rather than in {@link PipelineMapper} because a Mapper must stay a
     * pure entity-to-DTO conversion and must not itself query a repository.
     */
    private List<PipelineStageResponseDto> toStageResponseDtos(List<PipelineStage> stages) {
        return stages.stream()
                .map(stage -> PipelineMapper.toResponseDto(
                        stage, applicationRepository.countByCurrentStage_Id(stage.getId())))
                .toList();
    }

    /**
     * BR-PIPE-04: {@code requestedIds} must be exactly the same set as
     * {@code existingIds} - no missing id (a stage silently dropped out of
     * the ordering), no unknown/foreign id, and no duplicate (which would
     * otherwise silently drop a different stage out while leaving two rows
     * pointing at the same position).
     */
    private void validateReorderSet(List<Long> requestedIds, Set<Long> existingIds) {
        Set<Long> requestedSet = new HashSet<>(requestedIds);
        boolean sameSize = requestedSet.size() == requestedIds.size() && requestedSet.size() == existingIds.size();
        if (!sameSize || !requestedSet.equals(existingIds)) {
            throw new BadRequestException(ErrorCode.PIPELINE_STAGE_REORDER_MISMATCH);
        }
    }

    private static boolean isTerminalStageType(StageType stageType) {
        return stageType == StageType.TERMINAL_SUCCESS || stageType == StageType.TERMINAL_REJECTED;
    }

    private Department resolveDepartmentOrNull(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.DEPARTMENT_NOT_FOUND, departmentId));
    }

    private PipelineTemplate findTemplateOrThrow(Long templateId) {
        return pipelineTemplateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PIPELINE_TEMPLATE_NOT_FOUND, templateId));
    }
}
