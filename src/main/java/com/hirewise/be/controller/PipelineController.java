package com.hirewise.be.controller;

import com.hirewise.be.dto.request.CreatePipelineStageRequestDto;
import com.hirewise.be.dto.request.CreatePipelineTemplateRequestDto;
import com.hirewise.be.dto.request.ReorderPipelineStagesRequestDto;
import com.hirewise.be.dto.response.PipelineStageResponseDto;
import com.hirewise.be.dto.response.PipelineTemplateResponseDto;
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.security.CurrentUserPrincipal;
import com.hirewise.be.service.PipelineService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * UC-04/UC-05/UC-06: Pipeline Template + Stage configuration. Every
 * endpoint here requires {@code PIPELINE_MANAGE} (HR_ADMIN), enforced
 * inside {@link PipelineService} rather than duplicated as a role gate
 * here (see the {@code authorization} package).
 * <p>
 * RBAC per endpoint:
 * <ul>
 *   <li>{@code GET    /api/pipeline-templates}                                 - {@code PIPELINE_MANAGE}</li>
 *   <li>{@code POST   /api/pipeline-templates}                                 - {@code PIPELINE_MANAGE}</li>
 *   <li>{@code GET    /api/pipeline-templates/{templateId}/stages}             - {@code PIPELINE_MANAGE}</li>
 *   <li>{@code POST   /api/pipeline-templates/{templateId}/stages}             - {@code PIPELINE_MANAGE}</li>
 *   <li>{@code PATCH  /api/pipeline-templates/{templateId}/stages/reorder}     - {@code PIPELINE_MANAGE}</li>
 *   <li>{@code DELETE /api/pipeline-templates/{templateId}/stages/{stageId}}   - {@code PIPELINE_MANAGE}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/pipeline-templates")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class PipelineController {

    PipelineService pipelineService;

    /**
     * UC-04 step 1: lists every Pipeline Template. Requires {@code PIPELINE_MANAGE}.
     *
     * @param currentUser authenticated caller, used for authorization
     * @return every template, most recently created first
     */
    @GetMapping
    public ResponseEntity<List<PipelineTemplateResponseDto>> listTemplates(
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(pipelineService.listTemplates(currentUser));
    }

    /**
     * UC-04 AF-01: creates a new Pipeline Template. Requires {@code PIPELINE_MANAGE}.
     *
     * @param request     new template's name and optional owning department
     * @param currentUser authenticated caller, used for authorization and auditing
     * @return 201 Created with the created template
     */
    @PostMapping
    public ResponseEntity<PipelineTemplateResponseDto> createTemplate(
            @Valid @RequestBody CreatePipelineTemplateRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser) {
        PipelineTemplateResponseDto response = pipelineService.createTemplate(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * UC-04 step 1: lists the stages of one Pipeline Template, in Kanban
     * column order. Requires {@code PIPELINE_MANAGE}.
     *
     * @param templateId  id of the pipeline template
     * @param currentUser authenticated caller, used for authorization
     * @return the template's stages ordered by position
     */
    @GetMapping("/{templateId}/stages")
    public ResponseEntity<List<PipelineStageResponseDto>> listStages(
            @PathVariable Long templateId,
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(pipelineService.listStages(templateId, currentUser));
    }

    /**
     * UC-04 main flow: appends a new Stage to a Pipeline Template. Requires
     * {@code PIPELINE_MANAGE}.
     *
     * @param templateId  id of the pipeline template to add the stage to
     * @param request     new stage's name/code/type/terminal flag/SLA
     * @param currentUser authenticated caller, used for authorization and auditing
     * @return 201 Created with the created stage
     */
    @PostMapping("/{templateId}/stages")
    public ResponseEntity<PipelineStageResponseDto> createStage(
            @PathVariable Long templateId,
            @Valid @RequestBody CreatePipelineStageRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser) {
        PipelineStageResponseDto response = pipelineService.createStage(templateId, request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * UC-05 main flow: reorders every Stage of a Pipeline Template in one
     * shot. Requires {@code PIPELINE_MANAGE}.
     *
     * @param templateId  id of the pipeline template whose stages are being reordered
     * @param request     the full list of stage ids in the desired new order
     * @param currentUser authenticated caller, used for authorization and auditing
     * @return the template's stages in their new order
     */
    @PatchMapping("/{templateId}/stages/reorder")
    public ResponseEntity<List<PipelineStageResponseDto>> reorderStages(
            @PathVariable Long templateId,
            @Valid @RequestBody ReorderPipelineStagesRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(pipelineService.reorderStages(templateId, request, currentUser));
    }

    /**
     * UC-06 main flow: soft-deletes a Stage (blocked if any Application
     * still references it - EX-01/BR-PIPE-03). Requires {@code PIPELINE_MANAGE}.
     *
     * @param templateId  id of the pipeline template the stage belongs to
     * @param stageId     id of the stage to delete
     * @param currentUser authenticated caller, used for authorization and auditing
     * @return 204 No Content on success
     */
    @DeleteMapping("/{templateId}/stages/{stageId}")
    public ResponseEntity<Void> deleteStage(
            @PathVariable Long templateId,
            @PathVariable Long stageId,
            @CurrentUserPrincipal CurrentUser currentUser) {
        pipelineService.deleteStage(templateId, stageId, currentUser);
        return ResponseEntity.noContent().build();
    }
}
