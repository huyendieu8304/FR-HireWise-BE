package com.hirewise.be.controller;

import com.hirewise.be.dto.request.SaveScorecardTemplateRequestDto;
import com.hirewise.be.dto.response.ScorecardTemplateResponseDto;
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.security.CurrentUserPrincipal;
import com.hirewise.be.service.ScorecardTemplateService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * UC-27: HR Admin/Hiring Manager configures Scorecard Templates + criteria.
 * <p>
 * RBAC: every endpoint requires {@code SCORECARD_TEMPLATE_MANAGE} - see
 * {@code ScorecardTemplateService} for the Layer 3 (Access Scope) check,
 * which is scoped to the template's Job's department when {@code jobId} is
 * set, or skipped (Layer 2 only) for a company-wide template.
 */
@RestController
@RequestMapping("/api/scorecard-templates")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class ScorecardTemplateController {

    ScorecardTemplateService scorecardTemplateService;

    /**
     * UC-27 step 1: every Scorecard Template (global and job-scoped, any
     * status), most recently created first.
     */
    @GetMapping
    public ResponseEntity<List<ScorecardTemplateResponseDto>> list(@CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(scorecardTemplateService.listTemplates(currentUser));
    }

    /**
     * @param templateId id of the template to look up
     */
    @GetMapping("/{templateId}")
    public ResponseEntity<ScorecardTemplateResponseDto> get(
            @PathVariable UUID templateId, @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(scorecardTemplateService.getTemplate(templateId, currentUser));
    }

    /**
     * UC-27 main flow steps 1-4: creates a new template ({@code version = 1}).
     */
    @PostMapping
    public ResponseEntity<ScorecardTemplateResponseDto> create(
            @Valid @RequestBody SaveScorecardTemplateRequestDto request, @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(scorecardTemplateService.createTemplate(request, currentUser));
    }

    /**
     * UC-27 AF-01: edits a template - versions instead of overwriting if it
     * already has grading history (see {@code ScorecardTemplateService}).
     *
     * @param templateId id of the template to edit
     */
    @PutMapping("/{templateId}")
    public ResponseEntity<ScorecardTemplateResponseDto> update(
            @PathVariable UUID templateId,
            @Valid @RequestBody SaveScorecardTemplateRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(scorecardTemplateService.updateTemplate(templateId, request, currentUser));
    }
}
