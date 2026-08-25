package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.EmailTemplate;
import com.hirewise.be.domain.EmailTemplateStatus;
import com.hirewise.be.domain.PipelineStage;
import com.hirewise.be.dto.PagedResponseDto;
import com.hirewise.be.dto.request.CreateEmailTemplateRequestDto;
import com.hirewise.be.dto.request.UpdateEmailTemplateRequestDto;
import com.hirewise.be.dto.response.EmailTemplateResponseDto;
import com.hirewise.be.dto.response.PipelineStageResponseDto;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.mapper.EmailTemplateMapper;
import com.hirewise.be.repository.EmailTemplateRepository;
import com.hirewise.be.repository.PipelineStageRepository;
import com.hirewise.be.security.CurrentUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * UC-09: HR Admin manages the email template catalogue.
 * <p>
 * Business rules enforced here:
 * <ul>
 *   <li>BR-EMAILTPL-01: code is unique system-wide.</li>
 *   <li>BR-EMAILTPL-03: template linked to active stage may not be deleted.</li>
 *   <li>BR-EMAILTPL-04: version incremented when subject or body content changes.</li>
 * </ul>
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class EmailTemplateService {

    EmailTemplateRepository emailTemplateRepository;
    PipelineStageRepository pipelineStageRepository;
    AccessControlService accessControlService;
    Clock clock;

    public PagedResponseDto<EmailTemplateResponseDto> list(Pageable pageable, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.EMAIL_TEMPLATE_MANAGE, ResourceContext.none());
        Page<EmailTemplate> page = emailTemplateRepository.findAll(pageable);
        List<EmailTemplateResponseDto> content = page.getContent().stream()
                .map(EmailTemplateMapper::toResponseDto)
                .toList();
        return PagedResponseDto.from(page, content);
    }

    public EmailTemplateResponseDto getById(Long id, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.EMAIL_TEMPLATE_MANAGE, ResourceContext.none());
        return EmailTemplateMapper.toResponseDto(findOrThrow(id));
    }

    /**
     * Creates a new email template (UC-09 normal flow).
     * Saved with status=ACTIVE and version=1.
     */
    @Transactional
    public EmailTemplateResponseDto create(CreateEmailTemplateRequestDto request, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.EMAIL_TEMPLATE_MANAGE, ResourceContext.none());

        if (emailTemplateRepository.existsByCode(request.getCode())) {
            throw new BusinessConflictException(ErrorCode.EMAIL_TEMPLATE_CODE_DUPLICATE, request.getCode());
        }

        PipelineStage stage = resolveStageOrNull(request.getPipelineStageId());
        Instant now = Instant.now(clock);

        EmailTemplate template = EmailTemplate.builder()
                .code(request.getCode())
                .name(request.getName())
                .pipelineStage(stage)
                .subjectTemplate(request.getSubjectTemplate())
                .bodyTemplate(request.getBodyTemplate())
                .version(1)
                .status(EmailTemplateStatus.ACTIVE)
                .createdAt(now)
                .updatedAt(now)
                .build();

        emailTemplateRepository.save(template);
        log.info("Created email template: id={}, code={}", template.getId(), template.getCode());
        return EmailTemplateMapper.toResponseDto(template);
    }

    /**
     * Updates an existing email template (UC-09 AF-01).
     * Version incremented when subjectTemplate or bodyTemplate changes (BR-EMAILTPL-04).
     */
    @Transactional
    public EmailTemplateResponseDto update(Long id, UpdateEmailTemplateRequestDto request, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.EMAIL_TEMPLATE_MANAGE, ResourceContext.none());

        EmailTemplate template = findOrThrow(id);

        if (emailTemplateRepository.existsByCodeAndIdNot(request.getCode(), id)) {
            throw new BusinessConflictException(ErrorCode.EMAIL_TEMPLATE_CODE_DUPLICATE, request.getCode());
        }

        boolean contentChanged =
                !Objects.equals(template.getSubjectTemplate(), request.getSubjectTemplate())
                || !Objects.equals(template.getBodyTemplate(), request.getBodyTemplate());

        PipelineStage stage = resolveStageOrNull(request.getPipelineStageId());
        Instant now = Instant.now(clock);

        template.setName(request.getName());
        template.setCode(request.getCode());
        template.setPipelineStage(stage);
        template.setSubjectTemplate(request.getSubjectTemplate());
        template.setBodyTemplate(request.getBodyTemplate());
        template.setUpdatedAt(now);
        if (contentChanged) {
            template.setVersion(template.getVersion() + 1);
        }

        emailTemplateRepository.save(template);
        log.info("Updated email template: id={}, code={}, version={}", template.getId(), template.getCode(), template.getVersion());
        return EmailTemplateMapper.toResponseDto(template);
    }



    /**
     * Soft-deletes (deactivates) an email template (UC-09 AF-02).
     * BR-EMAILTPL-03/EX-01: blocked when linked to an active pipeline stage (ME-15).
     */
    @Transactional
    public void delete(Long id, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.EMAIL_TEMPLATE_MANAGE, ResourceContext.none());

        EmailTemplate template = findOrThrow(id);

        if (template.getPipelineStage() != null && template.getPipelineStage().isActive()) {
            throw new BusinessConflictException(ErrorCode.EMAIL_TEMPLATE_STAGE_CONFLICT);
        }

        template.setStatus(EmailTemplateStatus.INACTIVE);
        template.setUpdatedAt(Instant.now(clock));
        emailTemplateRepository.save(template);
        log.info("Deactivated email template: id={}, code={}", id, template.getCode());
    }

    /**
     * Lists active pipeline stages for the "gan Stage" dropdown on the template form.
     */
    public List<PipelineStageResponseDto> listPipelineStages(CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.EMAIL_TEMPLATE_MANAGE, ResourceContext.none());
        return pipelineStageRepository.findByActiveTrueOrderByPositionAsc().stream()
                .map(EmailTemplateMapper::toResponseDto)
                .toList();
    }

    private EmailTemplate findOrThrow(Long id) {
        return emailTemplateRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.EMAIL_TEMPLATE_NOT_FOUND, id));
    }

    private PipelineStage resolveStageOrNull(Long stageId) {
        if (stageId == null) return null;
        return pipelineStageRepository.findById(stageId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.RESOURCE_NOT_FOUND, stageId));
    }
}