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
 * UC-04/UC-05: HR Admin configuration of the recruitment Pipeline
 * (Pipeline Template + Stage) - creating stages (UC-04) and reordering
 * them (UC-05). Deleting a stage (UC-06) is a separate use case and is
 * not implemented here.
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
     *
     * @param templateId  id of the pipeline template
     * @param currentUser authenticated caller, must have {@code PIPELINE_MANAGE}
     * @return the template's stages ordered by position
     * @throws ResourceNotFoundException if no template exists with {@code templateId}
     */
    public List<PipelineStageResponseDto> listStages(Long templateId, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.PIPELINE_MANAGE, ResourceContext.none());
        findTemplateOrThrow(templateId);

        return pipelineStageRepository.findByPipelineTemplate_IdOrderByPositionAsc(templateId).stream()
                .map(PipelineMapper::toResponseDto)
                .toList();
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
        return PipelineMapper.toResponseDto(stage);
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

        List<PipelineStage> currentStages =
                pipelineStageRepository.findByPipelineTemplate_IdOrderByPositionAsc(templateId);
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
        return orderedIds.stream()
                .map(id -> PipelineMapper.toResponseDto(stageById.get(id)))
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
