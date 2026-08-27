package com.hirewise.be.controller;

import com.hirewise.be.dto.PagedResponseDto;
import com.hirewise.be.dto.request.CreateEmailTemplateRequestDto;
import com.hirewise.be.dto.request.UpdateEmailTemplateRequestDto;
import com.hirewise.be.dto.response.EmailTemplateResponseDto;
import com.hirewise.be.dto.response.PipelineStageResponseDto;
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.security.CurrentUserPrincipal;
import com.hirewise.be.service.EmailTemplateService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * UC-09: HR Admin manages the email template catalogue (create / edit / delete).
 * All endpoints require EMAIL_TEMPLATE_MANAGE permission.
 *
 * RBAC per endpoint:
 *   POST   /api/admin/email-templates                 - EMAIL_TEMPLATE_MANAGE
 *   GET    /api/admin/email-templates                 - EMAIL_TEMPLATE_MANAGE
 *   GET    /api/admin/email-templates/{id}            - EMAIL_TEMPLATE_MANAGE
 *   PUT    /api/admin/email-templates/{id}            - EMAIL_TEMPLATE_MANAGE
 *   DELETE /api/admin/email-templates/{id}            - EMAIL_TEMPLATE_MANAGE
 *   GET    /api/admin/email-templates/pipeline-stages - EMAIL_TEMPLATE_MANAGE
 */
@RestController
@RequestMapping("/api/admin/email-templates")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class EmailTemplateController {

    EmailTemplateService emailTemplateService;

    /**
     * Lists all email templates with pagination. Requires EMAIL_TEMPLATE_MANAGE.
     */
    @GetMapping
    public ResponseEntity<PagedResponseDto<EmailTemplateResponseDto>> list(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @CurrentUserPrincipal CurrentUser currentUser
    ) {
        int boundedSize = Math.min(Math.max(size, 1), 100);
        int boundedPage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(boundedPage, boundedSize, Sort.by(Sort.Direction.ASC, "id"));
        return ResponseEntity.ok(emailTemplateService.list(pageable, currentUser));
    }

    /**
     * Retrieves a single email template by id. Requires EMAIL_TEMPLATE_MANAGE.
     */
    @GetMapping("/{id}")
    public ResponseEntity<EmailTemplateResponseDto> getById(
            @PathVariable Long id,
            @CurrentUserPrincipal CurrentUser currentUser
    ) {
        return ResponseEntity.ok(emailTemplateService.getById(id, currentUser));
    }

    /**
     * Creates a new email template (UC-09 normal flow). Requires EMAIL_TEMPLATE_MANAGE.
     *
     * @return 201 Created with Location header
     */
    @PostMapping
    public ResponseEntity<EmailTemplateResponseDto> create(
            @Valid @RequestBody CreateEmailTemplateRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser
    ) {
        EmailTemplateResponseDto response = emailTemplateService.create(request, currentUser);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getId())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    /**
     * Updates an existing email template (UC-09 AF-01). Requires EMAIL_TEMPLATE_MANAGE.
     * Version is auto-incremented when content changes (BR-EMAILTPL-04).
     */
    @PutMapping("/{id}")
    public ResponseEntity<EmailTemplateResponseDto> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateEmailTemplateRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser
    ) {
        return ResponseEntity.ok(emailTemplateService.update(id, request, currentUser));
    }



    /**
     * Soft-deletes an email template (UC-09 AF-02). Requires EMAIL_TEMPLATE_MANAGE.
     * Returns 409 (ME-15) when template is linked to an active stage (BR-EMAILTPL-03/EX-01).
     *
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @CurrentUserPrincipal CurrentUser currentUser
    ) {
        emailTemplateService.delete(id, currentUser);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lists active pipeline stages for the stage dropdown on the template form (UC-09 step 2).
     * Requires EMAIL_TEMPLATE_MANAGE.
     */
    @GetMapping("/pipeline-stages")
    public ResponseEntity<List<PipelineStageResponseDto>> listPipelineStages(
            @CurrentUserPrincipal CurrentUser currentUser
    ) {
        return ResponseEntity.ok(emailTemplateService.listPipelineStages(currentUser));
    }
}