package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.Candidate;
import com.hirewise.be.domain.Department;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.Offer;
import com.hirewise.be.domain.OfferStatus;
import com.hirewise.be.domain.OfferTemplate;
import com.hirewise.be.domain.OfferTemplateStatus;
import com.hirewise.be.domain.PipelineStage;
import com.hirewise.be.domain.StageType;
import com.hirewise.be.domain.User;
import com.hirewise.be.dto.request.CreateOfferRequestDto;
import com.hirewise.be.dto.response.OfferResponseDto;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.repository.ApplicationRepository;
import com.hirewise.be.repository.OfferRepository;
import com.hirewise.be.repository.OfferTemplateRepository;
import com.hirewise.be.repository.UserRepository;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UC-36: generate an Offer Letter from a template (BR-OFFER-01/02). */
@ExtendWith(MockitoExtension.class)
class OfferServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-03T00:00:00Z");
    private static final UUID APPLICATION_ID = UUID.randomUUID();
    private static final Long TEMPLATE_ID = 1L;
    private static final Long DEPARTMENT_ID = 4L;
    private static final Long RECRUITER_ID = 7L;

    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private OfferRepository offerRepository;
    @Mock
    private OfferTemplateRepository offerTemplateRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AccessControlService accessControlService;

    private OfferService offerService;
    private CurrentUser recruiter;

    @BeforeEach
    void setUp() {
        offerService = new OfferService(
                applicationRepository,
                offerRepository,
                offerTemplateRepository,
                userRepository,
                new OfferTemplateRenderer("HireWise"),
                accessControlService,
                Clock.fixed(NOW, ZoneOffset.UTC));

        recruiter = new CurrentUser(RECRUITER_ID, "recruiter@hirewise.local", "Le Thi Recruiter",
                Set.of("RECRUITER"));
    }

    @Test
    void createsDraftOfferAndFreezesRenderedBody() {
        Application application = application(StageType.OFFER, "Offer");
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(offerRepository.existsByApplication_IdAndStatusIn(eq(APPLICATION_ID), anyCollection()))
                .thenReturn(false);
        when(offerTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(activeTemplate()));
        when(userRepository.getReferenceById(RECRUITER_ID)).thenReturn(recruiterUser());
        when(offerRepository.save(any(Offer.class))).thenAnswer(call -> call.getArgument(0));

        OfferResponseDto result = offerService.create(APPLICATION_ID, validRequest(), recruiter);

        assertThat(result.getStatus()).isEqualTo(OfferStatus.DRAFT.name());
        assertThat(result.getSentAt()).isNull();
        assertThat(result.getRenderedBody())
                .contains("Nguyen Van A")
                .contains("Backend Engineer")
                .contains("25.000.000 VND")
                .doesNotContain("{{");
    }

    @Test
    void appliesDefaultProbationRateWhenOmitted() {
        Application application = application(StageType.OFFER, "Offer");
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(offerRepository.existsByApplication_IdAndStatusIn(eq(APPLICATION_ID), anyCollection()))
                .thenReturn(false);
        when(offerTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(activeTemplate()));
        when(userRepository.getReferenceById(RECRUITER_ID)).thenReturn(recruiterUser());
        when(offerRepository.save(any(Offer.class))).thenAnswer(call -> call.getArgument(0));

        CreateOfferRequestDto request = validRequest();
        request.setProbationRate(null);

        OfferResponseDto result = offerService.create(APPLICATION_ID, request, recruiter);

        assertThat(result.getProbationRate()).isEqualByComparingTo(new BigDecimal("85.00"));
    }

    @Test
    void applicationNotInOfferStage_throwsBusinessConflict() {
        Application application = application(StageType.INTERVIEW, "Phong van");
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));

        assertThatThrownBy(() -> offerService.create(APPLICATION_ID, validRequest(), recruiter))
                .isInstanceOf(BusinessConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPLICATION_NOT_IN_OFFER_STAGE);

        verify(offerRepository, never()).save(any());
    }

    @Test
    void activeOfferAlreadyExists_throwsBusinessConflict() {
        Application application = application(StageType.OFFER, "Offer");
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(offerRepository.existsByApplication_IdAndStatusIn(eq(APPLICATION_ID), anyCollection()))
                .thenReturn(true);

        assertThatThrownBy(() -> offerService.create(APPLICATION_ID, validRequest(), recruiter))
                .isInstanceOf(BusinessConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OFFER_ALREADY_ACTIVE);

        verify(offerRepository, never()).save(any());
    }

    @Test
    void inactiveTemplate_throwsBusinessConflict() {
        Application application = application(StageType.OFFER, "Offer");
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(offerRepository.existsByApplication_IdAndStatusIn(eq(APPLICATION_ID), anyCollection()))
                .thenReturn(false);
        OfferTemplate template = activeTemplate();
        template.setStatus(OfferTemplateStatus.INACTIVE);
        when(offerTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));

        assertThatThrownBy(() -> offerService.create(APPLICATION_ID, validRequest(), recruiter))
                .isInstanceOf(BusinessConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OFFER_TEMPLATE_INACTIVE);
    }

    @Test
    void templateNotFound_throwsResourceNotFound() {
        Application application = application(StageType.OFFER, "Offer");
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(offerRepository.existsByApplication_IdAndStatusIn(eq(APPLICATION_ID), anyCollection()))
                .thenReturn(false);
        when(offerTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> offerService.create(APPLICATION_ID, validRequest(), recruiter))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OFFER_TEMPLATE_NOT_FOUND);
    }

    @Test
    void expiryAfterStartDate_throwsBusinessConflict() {
        Application application = application(StageType.OFFER, "Offer");
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(offerRepository.existsByApplication_IdAndStatusIn(eq(APPLICATION_ID), anyCollection()))
                .thenReturn(false);
        when(offerTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(activeTemplate()));

        CreateOfferRequestDto request = validRequest();
        request.setStartDate(LocalDate.of(2026, 9, 10));
        request.setExpiresAt(Instant.parse("2026-09-20T00:00:00Z"));

        assertThatThrownBy(() -> offerService.create(APPLICATION_ID, request, recruiter))
                .isInstanceOf(BusinessConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.OFFER_EXPIRY_BEFORE_START_DATE);
    }

    @Test
    void applicationNotFound_throwsResourceNotFound() {
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> offerService.create(APPLICATION_ID, validRequest(), recruiter))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.APPLICATION_NOT_FOUND);
    }

    @Test
    void listTemplates_checksOfferCreatePermissionOnJobDepartment() {
        Application application = application(StageType.OFFER, "Offer");
        when(applicationRepository.findById(APPLICATION_ID)).thenReturn(Optional.of(application));
        when(offerTemplateRepository.findSelectable(OfferTemplateStatus.ACTIVE, DEPARTMENT_ID))
                .thenReturn(List.of(activeTemplate()));

        assertThat(offerService.listTemplates(APPLICATION_ID, recruiter)).hasSize(1);

        ArgumentCaptor<com.hirewise.be.authorization.ResourceContext> context =
                ArgumentCaptor.forClass(com.hirewise.be.authorization.ResourceContext.class);
        verify(accessControlService).checkAccess(eq(recruiter),
                eq(com.hirewise.be.authorization.PermissionCodes.OFFER_CREATE), context.capture());
        assertThat(context.getValue().departmentId()).isEqualTo(DEPARTMENT_ID);
    }

    private CreateOfferRequestDto validRequest() {
        return new CreateOfferRequestDto(
                TEMPLATE_ID,
                new BigDecimal("25000000"),
                new BigDecimal("85"),
                LocalDate.of(2026, 10, 1),
                Instant.parse("2026-09-15T00:00:00Z"));
    }

    private OfferTemplate activeTemplate() {
        return OfferTemplate.builder()
                .id(TEMPLATE_ID)
                .name("Thu moi lam viec chuan")
                .version(1)
                .status(OfferTemplateStatus.ACTIVE)
                .bodyTemplate("<p>{{Candidate_Name}} - {{Job_Title}} - {{Salary}} - "
                        + "{{Probation_Rate}} - {{Start_Date}} - {{Expiry_Date}} - {{Recruiter_Name}}</p>")
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    private User recruiterUser() {
        User user = new User();
        user.setId(RECRUITER_ID);
        return user;
    }

    private Application application(StageType stageType, String stageName) {
        Department department = new Department();
        department.setId(DEPARTMENT_ID);

        JobPosition job = new JobPosition();
        job.setId(UUID.randomUUID());
        job.setTitle("Backend Engineer");
        job.setDepartment(department);
        job.setRecruiter(recruiterUser());

        Candidate candidate = new Candidate();
        candidate.setId(UUID.randomUUID());
        candidate.setFullName("Nguyen Van A");

        PipelineStage stage = new PipelineStage();
        stage.setId(9L);
        stage.setName(stageName);
        stage.setStageType(stageType);

        return Application.builder()
                .id(APPLICATION_ID)
                .candidate(candidate)
                .jobPosition(job)
                .currentStage(stage)
                .appliedAt(NOW)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }
}
