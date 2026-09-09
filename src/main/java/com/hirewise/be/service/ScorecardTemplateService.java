package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.ScorecardCriterion;
import com.hirewise.be.domain.ScorecardStatus;
import com.hirewise.be.domain.ScorecardTemplate;
import com.hirewise.be.domain.User;
import com.hirewise.be.dto.request.ScorecardCriterionInputDto;
import com.hirewise.be.dto.request.SaveScorecardTemplateRequestDto;
import com.hirewise.be.dto.response.ScorecardTemplateResponseDto;
import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.mapper.ScorecardMapper;
import com.hirewise.be.repository.ScorecardCriterionRepository;
import com.hirewise.be.repository.ScorecardTemplateRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * UC-27: HR Admin's MASTER SCORECARD TEMPLATE LIBRARY - always company-wide
 * reference/clone material (see {@link ScorecardTemplate}'s own Javadoc for
 * why this is never Job/Stage-scoped and never versioned). For the real
 * per-(Job, Stage) scoring definition Hiring Managers configure and that
 * evaluators actually score against, see {@code JobStageScorecardService}.
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class ScorecardTemplateService {

    ScorecardTemplateRepository scorecardTemplateRepository;
    ScorecardCriterionRepository scorecardCriterionRepository;
    AccessControlService accessControlService;
    Clock clock;

    /**
     * UC-27 step 1: every Master Template, most recently created first.
     *
     * @param currentUser authenticated caller, must have {@code SCORECARD_TEMPLATE_MANAGE}
     */
    public List<ScorecardTemplateResponseDto> listTemplates(CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.SCORECARD_TEMPLATE_MANAGE, ResourceContext.none());
        return scorecardTemplateRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponseDtoWithCriteria)
                .toList();
    }

    /**
     * @param templateId  id of the template to look up
     * @param currentUser authenticated caller, must have {@code SCORECARD_TEMPLATE_MANAGE}
     * @return the template with its ordered criteria
     * @throws ResourceNotFoundException if no template exists with {@code templateId}
     */
    public ScorecardTemplateResponseDto getTemplate(UUID templateId, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.SCORECARD_TEMPLATE_MANAGE, ResourceContext.none());
        ScorecardTemplate template = findTemplateOrThrow(templateId);
        return toResponseDtoWithCriteria(template);
    }

    /**
     * UC-27 main flow: creates a brand-new Master Template.
     *
     * @param request     template name and its full criteria list
     * @param currentUser HR Admin performing the creation
     * @return the created template
     * @throws BadRequestException if the total weight of all criteria is 0 (EX-01)
     */
    @Transactional
    public ScorecardTemplateResponseDto createTemplate(SaveScorecardTemplateRequestDto request, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.SCORECARD_TEMPLATE_MANAGE, ResourceContext.none());
        validateTotalWeight(request.getCriteria());

        Instant now = Instant.now(clock);
        ScorecardTemplate template = ScorecardTemplate.builder()
                .id(UUID.randomUUID())
                .name(request.getName())
                .status(ScorecardStatus.ACTIVE)
                .createdBy(User.builder().id(currentUser.userId()).build())
                .createdAt(now)
                .updatedAt(now)
                .build();
        scorecardTemplateRepository.save(template);

        List<ScorecardCriterion> criteria = buildCriteria(template, request.getCriteria());
        scorecardCriterionRepository.saveAll(criteria);

        log.info("Created Master scorecard template: {} (name={}, criteria={})",
                template.getId(), template.getName(), criteria.size());
        return ScorecardMapper.toResponseDto(template, criteria);
    }

    /**
     * UC-27: edits a Master Template - always in place (no versioning, see
     * {@link ScorecardTemplate}'s own Javadoc for why nothing depends on this
     * row for score reproducibility).
     *
     * @param templateId  id of the template to edit
     * @param request     new name/criteria (the COMPLETE set, not a delta)
     * @param currentUser HR Admin performing the edit
     * @return the updated template
     * @throws ResourceNotFoundException if no template exists with {@code templateId}
     * @throws BadRequestException       if the total weight of all criteria is 0 (EX-01)
     */
    @Transactional
    public ScorecardTemplateResponseDto updateTemplate(
            UUID templateId, SaveScorecardTemplateRequestDto request, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.SCORECARD_TEMPLATE_MANAGE, ResourceContext.none());
        ScorecardTemplate template = findTemplateOrThrow(templateId);
        validateTotalWeight(request.getCriteria());

        template.setName(request.getName());
        template.setUpdatedAt(Instant.now(clock));
        scorecardTemplateRepository.save(template);

        scorecardCriterionRepository.deleteByScorecardTemplate_Id(templateId);
        List<ScorecardCriterion> criteria = buildCriteria(template, request.getCriteria());
        scorecardCriterionRepository.saveAll(criteria);

        log.info("Edited Master scorecard template: {}", templateId);
        return ScorecardMapper.toResponseDto(template, criteria);
    }

    /**
     * Builds the criteria rows in list order ({@code position = index + 1}).
     * Deliberately an indexed loop, not {@code inputs.indexOf(input)} inside
     * a stream - Lombok's generated {@code equals()} on
     * {@link ScorecardCriterionInputDto} means two criteria with identical
     * field values (a real possibility) would make {@code indexOf} return
     * the FIRST matching index for both, silently mis-numbering
     * {@code position} for one of them.
     */
    private List<ScorecardCriterion> buildCriteria(ScorecardTemplate template, List<ScorecardCriterionInputDto> inputs) {
        List<ScorecardCriterion> criteria = new ArrayList<>(inputs.size());
        for (int index = 0; index < inputs.size(); index++) {
            ScorecardCriterionInputDto input = inputs.get(index);
            criteria.add(ScorecardCriterion.builder()
                    .id(UUID.randomUUID())
                    .scorecardTemplate(template)
                    .name(input.getName())
                    .description(input.getDescription())
                    .weight(input.getWeight())
                    .maxScore(input.getMaxScore())
                    .position(index + 1)
                    .required(input.isRequired())
                    .build());
        }
        return criteria;
    }

    /** EX-01: "He thong canh bao neu tong weight = 0, chan luu." */
    static void validateTotalWeight(List<ScorecardCriterionInputDto> criteria) {
        BigDecimal totalWeight = criteria.stream()
                .map(ScorecardCriterionInputDto::getWeight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException(ErrorCode.SCORECARD_TEMPLATE_TOTAL_WEIGHT_ZERO);
        }
    }

    private ScorecardTemplate findTemplateOrThrow(UUID templateId) {
        return scorecardTemplateRepository.findById(templateId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SCORECARD_TEMPLATE_NOT_FOUND, templateId));
    }

    private ScorecardTemplateResponseDto toResponseDtoWithCriteria(ScorecardTemplate template) {
        List<ScorecardCriterion> criteria =
                scorecardCriterionRepository.findByScorecardTemplate_IdOrderByPositionAsc(template.getId());
        return ScorecardMapper.toResponseDto(template, criteria);
    }
}
