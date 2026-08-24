package com.hirewise.be.controller;

import com.hirewise.be.dto.request.CreatePipelineStageRequestDto;
import com.hirewise.be.dto.request.CreatePipelineTemplateRequestDto;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * UC-04: Pipeline Template + Stage configuration. Every endpoint here
 * requires {@code PIPELINE_MANAGE} (HR_ADMIN), enforced inside
 * {@link PipelineService} rather than duplicated as a role gate here (see
 * the {@code authorization} package).
 * <p>
 * RBAC per endpoint:
 * <ul>
 *   <li>{@code GET  /api/pipeline-templates}                     - {@code PIPELINE_MANAGE}</li>
 *   <li>{@code POST /api/pipeline-templates}                     - {@code PIPELINE_MANAGE}</li>
 *   <li>{@code GET  /api/pipeline-templates/{templateId}/stages} - {@code PIPELINE_MANAGE}</li>
 *   <li>{@code POST /api/pipeline-templates/{templateId}/stages} - {@code PIPELINE_MANAGE}</li>
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
}
