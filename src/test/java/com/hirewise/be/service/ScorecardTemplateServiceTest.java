package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.ScorecardCriterion;
import com.hirewise.be.domain.ScorecardStatus;
import com.hirewise.be.domain.ScorecardTemplate;
import com.hirewise.be.dto.request.ScorecardCriterionInputDto;
import com.hirewise.be.dto.request.SaveScorecardTemplateRequestDto;
import com.hirewise.be.dto.response.ScorecardTemplateResponseDto;
import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.repository.ScorecardCriterionRepository;
import com.hirewise.be.repository.ScorecardTemplateRepository;
import com.hirewise.be.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC-27: HR Admin's Master Scorecard Template library - always company-wide,
 * always edited in place (no versioning, no Job/Stage scoping - see
 * {@link JobStageScorecardServiceTest} for the real per-(Job, Stage)
 * scoring definition that DOES version, UC-27 step 3).
 */
@ExtendWith(MockitoExtension.class)
class ScorecardTemplateServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T00:00:00Z");
    private static final UUID TEMPLATE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock private ScorecardTemplateRepository scorecardTemplateRepository;
    @Mock private ScorecardCriterionRepository scorecardCriterionRepository;
    @Mock private AccessControlService accessControlService;

    private ScorecardTemplateService service;
    private final CurrentUser currentUser = new CurrentUser(1L, "hr@test.com", "HR Admin", Set.of("HR_ADMIN"));

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new ScorecardTemplateService(scorecardTemplateRepository, scorecardCriterionRepository,
                accessControlService, clock);
    }

    private ScorecardCriterionInputDto criterion(String name, String weight, String maxScore, boolean required) {
        ScorecardCriterionInputDto dto = new ScorecardCriterionInputDto();
        dto.setName(name);
        dto.setWeight(new BigDecimal(weight));
        dto.setMaxScore(new BigDecimal(maxScore));
        dto.setRequired(required);
        return dto;
    }

    private SaveScorecardTemplateRequestDto request(ScorecardCriterionInputDto... criteria) {
        return new SaveScorecardTemplateRequestDto("Technical - Backend", List.of(criteria));
    }

    @Test
    void createTemplate_totalWeightZero_throwsBadRequest() {
        SaveScorecardTemplateRequestDto request = request(criterion("Communication", "0", "5", true));

        assertThatThrownBy(() -> service.createTemplate(request, currentUser))
                .isInstanceOf(BadRequestException.class);

        verify(scorecardTemplateRepository, never()).save(any());
    }

    @Test
    void createTemplate_savesTemplateAndCriteria_alwaysGlobalNeverVersioned() {
        SaveScorecardTemplateRequestDto request = request(
                criterion("Technical depth", "60", "5", true),
                criterion("Communication", "40", "5", false));

        ScorecardTemplateResponseDto result = service.createTemplate(request, currentUser);

        verify(accessControlService).checkAccess(eq(currentUser), eq(PermissionCodes.SCORECARD_TEMPLATE_MANAGE),
                eq(ResourceContext.none()));
        ArgumentCaptor<ScorecardTemplate> templateCaptor = ArgumentCaptor.forClass(ScorecardTemplate.class);
        verify(scorecardTemplateRepository).save(templateCaptor.capture());
        assertThat(templateCaptor.getValue().getStatus()).isEqualTo(ScorecardStatus.ACTIVE);

        ArgumentCaptor<List<ScorecardCriterion>> criteriaCaptor = ArgumentCaptor.forClass(List.class);
        verify(scorecardCriterionRepository).saveAll(criteriaCaptor.capture());
        assertThat(criteriaCaptor.getValue()).hasSize(2);
        assertThat(criteriaCaptor.getValue().get(0).getPosition()).isEqualTo(1);
        assertThat(criteriaCaptor.getValue().get(1).getPosition()).isEqualTo(2);
        assertThat(result.getCriteria()).hasSize(2);
    }

    @Test
    void updateTemplate_alwaysEditsInPlace_noVersioningEvenIfCloned() {
        ScorecardTemplate existing = ScorecardTemplate.builder()
                .id(TEMPLATE_ID).name("Old name").status(ScorecardStatus.ACTIVE)
                .createdAt(NOW).updatedAt(NOW).build();
        when(scorecardTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(existing));

        SaveScorecardTemplateRequestDto request = request(criterion("New criterion", "100", "5", true));
        ScorecardTemplateResponseDto result = service.updateTemplate(TEMPLATE_ID, request, currentUser);

        // No versioning concept here at all - a Master Template is reference/clone material
        // only, nothing scores against it directly, so editing it always mutates in place.
        assertThat(result.getId()).isEqualTo(TEMPLATE_ID);
        assertThat(existing.getName()).isEqualTo("Technical - Backend");
        assertThat(existing.getStatus()).isEqualTo(ScorecardStatus.ACTIVE);
        verify(scorecardCriterionRepository).deleteByScorecardTemplate_Id(TEMPLATE_ID);
        verify(scorecardCriterionRepository).saveAll(any());
    }

    @Test
    void updateTemplate_notFound_throwsResourceNotFound() {
        when(scorecardTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateTemplate(TEMPLATE_ID, request(criterion("A", "1", "5", true)), currentUser))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getTemplate_notFound_throwsResourceNotFound() {
        when(scorecardTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTemplate(TEMPLATE_ID, currentUser))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listTemplates_checksAccessAndReturnsEveryTemplateWithCriteria() {
        ScorecardTemplate template = ScorecardTemplate.builder()
                .id(TEMPLATE_ID).name("T").status(ScorecardStatus.ACTIVE)
                .createdAt(NOW).updatedAt(NOW).build();
        when(scorecardTemplateRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(template));
        when(scorecardCriterionRepository.findByScorecardTemplate_IdOrderByPositionAsc(TEMPLATE_ID))
                .thenReturn(List.of());

        List<ScorecardTemplateResponseDto> result = service.listTemplates(currentUser);

        verify(accessControlService).checkAccess(currentUser, PermissionCodes.SCORECARD_TEMPLATE_MANAGE, ResourceContext.none());
        assertThat(result).hasSize(1);
    }
}
