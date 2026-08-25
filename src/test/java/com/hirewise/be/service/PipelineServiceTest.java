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
import com.hirewise.be.dto.response.PipelineStageResponseDto;
import com.hirewise.be.dto.response.PipelineTemplateResponseDto;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.PermissionDeniedException;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.repository.DepartmentRepository;
import com.hirewise.be.repository.PipelineStageRepository;
import com.hirewise.be.repository.PipelineTemplateRepository;
import com.hirewise.be.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC-04: Pipeline Template + Stage creation.
 */
@ExtendWith(MockitoExtension.class)
class PipelineServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-24T00:00:00Z");
    private static final Long TEMPLATE_ID = 10L;
    private static final Long DEPARTMENT_ID = 4L;

    @Mock
    private PipelineTemplateRepository pipelineTemplateRepository;
    @Mock
    private PipelineStageRepository pipelineStageRepository;
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private AccessControlService accessControlService;

    private PipelineService pipelineService;
    private CurrentUser hrAdmin;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        pipelineService = new PipelineService(
                pipelineTemplateRepository, pipelineStageRepository, departmentRepository,
                accessControlService, fixedClock);
        hrAdmin = new CurrentUser(1L, "admin@hirewise.com", "HR Admin", Set.of("HR_ADMIN"));
    }

    private PipelineTemplate templateWithDepartment(Long departmentId) {
        Department department = departmentId != null
                ? Department.builder().id(departmentId).name("Engineering").build()
                : null;
        return PipelineTemplate.builder()
                .id(TEMPLATE_ID)
                .name("Default")
                .department(department)
                .status(PipelineTemplateStatus.DRAFT)
                .createdAt(NOW)
                .updatedAt(NOW)
                .build();
    }

    @Test
    void createTemplate_savesAsDraftWithResolvedDepartment() {
        CreatePipelineTemplateRequestDto request =
                new CreatePipelineTemplateRequestDto("IT Track", DEPARTMENT_ID);
        Department department = Department.builder().id(DEPARTMENT_ID).name("Engineering").build();
        when(departmentRepository.findById(DEPARTMENT_ID)).thenReturn(Optional.of(department));

        PipelineTemplateResponseDto response = pipelineService.createTemplate(request, hrAdmin);

        assertThat(response.getName()).isEqualTo("IT Track");
        assertThat(response.getDepartmentId()).isEqualTo(DEPARTMENT_ID);
        assertThat(response.getStatus()).isEqualTo(PipelineTemplateStatus.DRAFT);
        verify(accessControlService).checkAccess(hrAdmin, PermissionCodes.PIPELINE_MANAGE,
                ResourceContext.department(DEPARTMENT_ID));
    }

    @Test
    void createTemplate_companyWide_departmentFieldsAreNull() {
        CreatePipelineTemplateRequestDto request = new CreatePipelineTemplateRequestDto("Default", null);

        PipelineTemplateResponseDto response = pipelineService.createTemplate(request, hrAdmin);

        assertThat(response.getDepartmentId()).isNull();
        assertThat(response.getDepartmentName()).isNull();
        verify(departmentRepository, never()).findById(any());
    }

    @Test
    void createTemplate_unknownDepartment_throwsResourceNotFound() {
        CreatePipelineTemplateRequestDto request =
                new CreatePipelineTemplateRequestDto("IT Track", DEPARTMENT_ID);
        when(departmentRepository.findById(DEPARTMENT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pipelineService.createTemplate(request, hrAdmin))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.DEPARTMENT_NOT_FOUND);
    }

    @Test
    void createTemplate_deniedWhenNoRoleGrantsPermission() {
        CreatePipelineTemplateRequestDto request = new CreatePipelineTemplateRequestDto("Default", null);
        doThrow(new PermissionDeniedException()).when(accessControlService)
                .checkAccess(eq(hrAdmin), eq(PermissionCodes.PIPELINE_MANAGE), any());

        assertThatThrownBy(() -> pipelineService.createTemplate(request, hrAdmin))
                .isInstanceOf(PermissionDeniedException.class);
        verify(pipelineTemplateRepository, never()).save(any());
    }

    @Test
    void listStages_unknownTemplate_throwsResourceNotFound() {
        when(pipelineTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pipelineService.listStages(TEMPLATE_ID, hrAdmin))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PIPELINE_TEMPLATE_NOT_FOUND);
    }

    @Test
    void listStages_returnsStagesOrderedByPosition() {
        when(pipelineTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(templateWithDepartment(null)));
        PipelineStage stage = PipelineStage.builder()
                .id(1L).pipelineTemplate(templateWithDepartment(null)).name("New").code("NEW")
                .stageType(StageType.INTAKE).position(1).terminal(false).active(true)
                .createdAt(NOW).updatedAt(NOW).build();
        when(pipelineStageRepository.findByPipelineTemplate_IdOrderByPositionAsc(TEMPLATE_ID))
                .thenReturn(List.of(stage));

        List<PipelineStageResponseDto> stages = pipelineService.listStages(TEMPLATE_ID, hrAdmin);

        assertThat(stages).hasSize(1);
        assertThat(stages.get(0).getCode()).isEqualTo("NEW");
    }

    @Test
    void createStage_unknownTemplate_throwsResourceNotFound() {
        when(pipelineTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.empty());
        CreatePipelineStageRequestDto request =
                new CreatePipelineStageRequestDto("New", "NEW", StageType.INTAKE, false, null);

        assertThatThrownBy(() -> pipelineService.createStage(TEMPLATE_ID, request, hrAdmin))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PIPELINE_TEMPLATE_NOT_FOUND);
        verify(accessControlService, never()).checkAccess(any(), any(), any());
    }

    @Test
    void createStage_duplicateCodeInSameTemplate_throwsBusinessConflict_EX01() {
        when(pipelineTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(templateWithDepartment(null)));
        when(pipelineStageRepository.existsByPipelineTemplate_IdAndCode(TEMPLATE_ID, "NEW")).thenReturn(true);
        CreatePipelineStageRequestDto request =
                new CreatePipelineStageRequestDto("New", "NEW", StageType.INTAKE, false, null);

        assertThatThrownBy(() -> pipelineService.createStage(TEMPLATE_ID, request, hrAdmin))
                .isInstanceOf(BusinessConflictException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PIPELINE_STAGE_CODE_ALREADY_EXISTS);
        verify(pipelineStageRepository, never()).save(any());
    }

    @Test
    void createStage_appendsAtNextPosition_BR_PIPE_04() {
        when(pipelineTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(templateWithDepartment(null)));
        when(pipelineStageRepository.existsByPipelineTemplate_IdAndCode(TEMPLATE_ID, "INTERVIEW")).thenReturn(false);
        when(pipelineStageRepository.findMaxPosition(TEMPLATE_ID)).thenReturn(2);
        CreatePipelineStageRequestDto request =
                new CreatePipelineStageRequestDto("Interview", "INTERVIEW", StageType.INTERVIEW, false, 48);

        PipelineStageResponseDto response = pipelineService.createStage(TEMPLATE_ID, request, hrAdmin);

        assertThat(response.getPosition()).isEqualTo(3);
        assertThat(response.getSlaHours()).isEqualTo(48);
        assertThat(response.isTerminal()).isFalse();
        ArgumentCaptor<PipelineStage> captor = ArgumentCaptor.forClass(PipelineStage.class);
        verify(pipelineStageRepository).save(captor.capture());
        assertThat(captor.getValue().getPosition()).isEqualTo(3);
    }

    @Test
    void createStage_terminalSuccessType_forcesIsTerminalTrueEvenIfCheckboxFalse() {
        when(pipelineTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(templateWithDepartment(null)));
        when(pipelineStageRepository.existsByPipelineTemplate_IdAndCode(TEMPLATE_ID, "HIRED")).thenReturn(false);
        when(pipelineStageRepository.findMaxPosition(TEMPLATE_ID)).thenReturn(4);
        // "Is Terminal" checkbox left unchecked (false) on purpose - stageType must still win.
        CreatePipelineStageRequestDto request =
                new CreatePipelineStageRequestDto("Hired", "HIRED", StageType.TERMINAL_SUCCESS, false, null);

        PipelineStageResponseDto response = pipelineService.createStage(TEMPLATE_ID, request, hrAdmin);

        assertThat(response.isTerminal()).isTrue();
    }

    @Test
    void createStage_departmentScopedTemplate_checksAccessWithTemplateDepartment() {
        when(pipelineTemplateRepository.findById(TEMPLATE_ID))
                .thenReturn(Optional.of(templateWithDepartment(DEPARTMENT_ID)));
        when(pipelineStageRepository.existsByPipelineTemplate_IdAndCode(TEMPLATE_ID, "TEST")).thenReturn(false);
        when(pipelineStageRepository.findMaxPosition(TEMPLATE_ID)).thenReturn(0);
        CreatePipelineStageRequestDto request =
                new CreatePipelineStageRequestDto("Technical Test", "TEST", StageType.SCREENING, false, null);

        pipelineService.createStage(TEMPLATE_ID, request, hrAdmin);

        // The stage itself has no department field - scope must be derived from its
        // PARENT TEMPLATE's department, not left as ResourceContext.none().
        verify(accessControlService).checkAccess(hrAdmin, PermissionCodes.PIPELINE_MANAGE,
                ResourceContext.department(DEPARTMENT_ID));
    }
}
