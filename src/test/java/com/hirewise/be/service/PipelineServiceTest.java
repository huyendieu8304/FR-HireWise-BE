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
import com.hirewise.be.exception.PermissionDeniedException;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.repository.ApplicationRepository;
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
 * UC-04/UC-05/UC-06: Pipeline Template + Stage creation, reordering, and deletion.
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
    private ApplicationRepository applicationRepository;
    @Mock
    private AccessControlService accessControlService;

    private PipelineService pipelineService;
    private CurrentUser hrAdmin;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(NOW, ZoneOffset.UTC);
        pipelineService = new PipelineService(
                pipelineTemplateRepository, pipelineStageRepository, departmentRepository,
                applicationRepository, accessControlService, fixedClock);
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

    private PipelineStage stage(Long id, int position, PipelineTemplate template) {
        return PipelineStage.builder()
                .id(id).pipelineTemplate(template).name("Stage " + id).code("STAGE_" + id)
                .stageType(StageType.SCREENING).position(position).terminal(false).active(true)
                .createdAt(NOW).updatedAt(NOW).build();
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
        when(pipelineStageRepository.findByPipelineTemplate_IdAndActiveTrueOrderByPositionAsc(TEMPLATE_ID))
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

    @Test
    void reorderStages_unknownTemplate_throwsResourceNotFound() {
        when(pipelineTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.empty());
        ReorderPipelineStagesRequestDto request = new ReorderPipelineStagesRequestDto(List.of(1L, 2L));

        assertThatThrownBy(() -> pipelineService.reorderStages(TEMPLATE_ID, request, hrAdmin))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PIPELINE_TEMPLATE_NOT_FOUND);
        verify(accessControlService, never()).checkAccess(any(), any(), any());
    }

    @Test
    void reorderStages_reassignsPositionsInRequestedOrder_BR_PIPE_04() {
        PipelineTemplate template = templateWithDepartment(null);
        PipelineStage stage1 = stage(1L, 1, template);
        PipelineStage stage2 = stage(2L, 2, template);
        PipelineStage stage3 = stage(3L, 3, template);
        when(pipelineTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
        when(pipelineStageRepository.findByPipelineTemplate_IdAndActiveTrueOrderByPositionAsc(TEMPLATE_ID))
                .thenReturn(List.of(stage1, stage2, stage3));
        // HR Admin kéo Stage 3 lên đầu: thứ tự mới mong muốn là [3, 1, 2].
        ReorderPipelineStagesRequestDto request = new ReorderPipelineStagesRequestDto(List.of(3L, 1L, 2L));

        List<PipelineStageResponseDto> response = pipelineService.reorderStages(TEMPLATE_ID, request, hrAdmin);

        assertThat(response).extracting(PipelineStageResponseDto::getId).containsExactly(3L, 1L, 2L);
        assertThat(response).extracting(PipelineStageResponseDto::getPosition).containsExactly(1, 2, 3);
        assertThat(stage3.getPosition()).isEqualTo(1);
        assertThat(stage1.getPosition()).isEqualTo(2);
        assertThat(stage2.getPosition()).isEqualTo(3);
        verify(pipelineStageRepository).saveAll(List.of(stage1, stage2, stage3));
    }

    @Test
    void reorderStages_missingStageId_throwsBadRequest_BR_PIPE_04() {
        PipelineTemplate template = templateWithDepartment(null);
        PipelineStage stage1 = stage(1L, 1, template);
        PipelineStage stage2 = stage(2L, 2, template);
        when(pipelineTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
        when(pipelineStageRepository.findByPipelineTemplate_IdAndActiveTrueOrderByPositionAsc(TEMPLATE_ID))
                .thenReturn(List.of(stage1, stage2));
        // Thiếu stage id 2L trong danh sách gửi lên -> phải chặn, không được âm thầm bỏ rơi 1 stage.
        ReorderPipelineStagesRequestDto request = new ReorderPipelineStagesRequestDto(List.of(1L));

        assertThatThrownBy(() -> pipelineService.reorderStages(TEMPLATE_ID, request, hrAdmin))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PIPELINE_STAGE_REORDER_MISMATCH);
        verify(pipelineStageRepository, never()).saveAll(any());
    }

    @Test
    void reorderStages_unknownForeignStageId_throwsBadRequest() {
        PipelineTemplate template = templateWithDepartment(null);
        PipelineStage stage1 = stage(1L, 1, template);
        PipelineStage stage2 = stage(2L, 2, template);
        when(pipelineTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
        when(pipelineStageRepository.findByPipelineTemplate_IdAndActiveTrueOrderByPositionAsc(TEMPLATE_ID))
                .thenReturn(List.of(stage1, stage2));
        // 999L không thuộc Template này (vd thuộc Template khác) -> phải chặn.
        ReorderPipelineStagesRequestDto request = new ReorderPipelineStagesRequestDto(List.of(1L, 999L));

        assertThatThrownBy(() -> pipelineService.reorderStages(TEMPLATE_ID, request, hrAdmin))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PIPELINE_STAGE_REORDER_MISMATCH);
        verify(pipelineStageRepository, never()).saveAll(any());
    }

    @Test
    void reorderStages_duplicateStageId_throwsBadRequest() {
        PipelineTemplate template = templateWithDepartment(null);
        PipelineStage stage1 = stage(1L, 1, template);
        PipelineStage stage2 = stage(2L, 2, template);
        when(pipelineTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
        when(pipelineStageRepository.findByPipelineTemplate_IdAndActiveTrueOrderByPositionAsc(TEMPLATE_ID))
                .thenReturn(List.of(stage1, stage2));
        // 1L bị lặp lại, đồng nghĩa 2L bị rơi ra khỏi danh sách dù size trùng khớp ngẫu nhiên.
        ReorderPipelineStagesRequestDto request = new ReorderPipelineStagesRequestDto(List.of(1L, 1L));

        assertThatThrownBy(() -> pipelineService.reorderStages(TEMPLATE_ID, request, hrAdmin))
                .isInstanceOf(BadRequestException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PIPELINE_STAGE_REORDER_MISMATCH);
        verify(pipelineStageRepository, never()).saveAll(any());
    }

    @Test
    void reorderStages_departmentScopedTemplate_checksAccessWithTemplateDepartment() {
        PipelineTemplate template = templateWithDepartment(DEPARTMENT_ID);
        PipelineStage stage1 = stage(1L, 1, template);
        PipelineStage stage2 = stage(2L, 2, template);
        when(pipelineTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
        when(pipelineStageRepository.findByPipelineTemplate_IdAndActiveTrueOrderByPositionAsc(TEMPLATE_ID))
                .thenReturn(List.of(stage1, stage2));
        ReorderPipelineStagesRequestDto request = new ReorderPipelineStagesRequestDto(List.of(2L, 1L));

        pipelineService.reorderStages(TEMPLATE_ID, request, hrAdmin);

        verify(accessControlService).checkAccess(hrAdmin, PermissionCodes.PIPELINE_MANAGE,
                ResourceContext.department(DEPARTMENT_ID));
    }

    @Test
    void deleteStage_unknownTemplate_throwsResourceNotFound() {
        when(pipelineTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pipelineService.deleteStage(TEMPLATE_ID, 1L, hrAdmin))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PIPELINE_TEMPLATE_NOT_FOUND);
        verify(accessControlService, never()).checkAccess(any(), any(), any());
    }

    @Test
    void deleteStage_stageBelongsToAnotherTemplate_throwsResourceNotFound() {
        PipelineTemplate template = templateWithDepartment(null);
        PipelineTemplate otherTemplate = PipelineTemplate.builder().id(99L).name("Other")
                .status(PipelineTemplateStatus.DRAFT).createdAt(NOW).updatedAt(NOW).build();
        PipelineStage stageOfOtherTemplate = stage(1L, 1, otherTemplate);
        when(pipelineTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
        when(pipelineStageRepository.findById(1L)).thenReturn(Optional.of(stageOfOtherTemplate));

        assertThatThrownBy(() -> pipelineService.deleteStage(TEMPLATE_ID, 1L, hrAdmin))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PIPELINE_STAGE_NOT_FOUND);
        verify(pipelineStageRepository, never()).save(any());
    }

    @Test
    void deleteStage_alreadyInactive_throwsResourceNotFound() {
        PipelineTemplate template = templateWithDepartment(null);
        PipelineStage alreadyDeletedStage = PipelineStage.builder()
                .id(2L).pipelineTemplate(template).name("Hired").code("HIRED")
                .stageType(StageType.TERMINAL_SUCCESS).position(2).terminal(true).active(false)
                .createdAt(NOW).updatedAt(NOW).build();
        when(pipelineTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
        when(pipelineStageRepository.findById(2L)).thenReturn(Optional.of(alreadyDeletedStage));

        // Xóa lại 1 Stage ĐÃ bị soft-delete từ trước phải báo "không tìm thấy", không được
        // âm thầm "thành công" lần 2 (findById không tự lọc active, phải lọc thủ công).
        assertThatThrownBy(() -> pipelineService.deleteStage(TEMPLATE_ID, 2L, hrAdmin))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PIPELINE_STAGE_NOT_FOUND);
        verify(applicationRepository, never()).countByCurrentStage_Id(any());
        verify(pipelineStageRepository, never()).save(any());
    }

    @Test
    void deleteStage_hasApplications_throwsBusinessConflict_EX01() {
        PipelineTemplate template = templateWithDepartment(null);
        PipelineStage stage1 = stage(1L, 1, template);
        when(pipelineTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
        when(pipelineStageRepository.findById(1L)).thenReturn(Optional.of(stage1));
        when(applicationRepository.countByCurrentStage_Id(1L)).thenReturn(3L);

        assertThatThrownBy(() -> pipelineService.deleteStage(TEMPLATE_ID, 1L, hrAdmin))
                .isInstanceOf(BusinessConflictException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PIPELINE_STAGE_HAS_APPLICATIONS);
        verify(pipelineStageRepository, never()).save(any());
    }

    @Test
    void deleteStage_noApplications_softDeletesAndReindexesRemaining_BR_PIPE_04() {
        PipelineTemplate template = templateWithDepartment(null);
        PipelineStage stage1 = stage(1L, 1, template);
        PipelineStage stage2 = stage(2L, 2, template);
        PipelineStage stage3 = stage(3L, 3, template);
        when(pipelineTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
        // Xóa stage2 (position=2, ở giữa) -> stage3 phải được re-index về position=2 (không để hở 1,3).
        when(pipelineStageRepository.findById(2L)).thenReturn(Optional.of(stage2));
        when(applicationRepository.countByCurrentStage_Id(2L)).thenReturn(0L);
        when(pipelineStageRepository.findByPipelineTemplate_IdAndActiveTrueOrderByPositionAsc(TEMPLATE_ID))
                .thenReturn(List.of(stage1, stage3));

        pipelineService.deleteStage(TEMPLATE_ID, 2L, hrAdmin);

        assertThat(stage2.isActive()).isFalse();
        assertThat(stage1.getPosition()).isEqualTo(1);
        assertThat(stage3.getPosition()).isEqualTo(2);
        verify(pipelineStageRepository).save(stage2);
        verify(pipelineStageRepository).saveAll(List.of(stage1, stage3));
    }

    @Test
    void deleteStage_departmentScopedTemplate_checksAccessWithTemplateDepartment() {
        PipelineTemplate template = templateWithDepartment(DEPARTMENT_ID);
        PipelineStage stage1 = stage(1L, 1, template);
        when(pipelineTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
        when(pipelineStageRepository.findById(1L)).thenReturn(Optional.of(stage1));
        when(pipelineStageRepository.findByPipelineTemplate_IdAndActiveTrueOrderByPositionAsc(TEMPLATE_ID))
                .thenReturn(List.of());

        pipelineService.deleteStage(TEMPLATE_ID, 1L, hrAdmin);

        verify(accessControlService).checkAccess(hrAdmin, PermissionCodes.PIPELINE_MANAGE,
                ResourceContext.department(DEPARTMENT_ID));
    }

    // -------------------------------------------------------------------
    // Activate Pipeline Template (prerequisite added for UC-13)
    // -------------------------------------------------------------------

    private PipelineStage stageOfType(Long id, int position, StageType stageType, PipelineTemplate template) {
        return PipelineStage.builder()
                .id(id).pipelineTemplate(template).name("Stage " + id).code("STAGE_" + id)
                .stageType(stageType).position(position).terminal(false).active(true)
                .createdAt(NOW).updatedAt(NOW).build();
    }

    @Test
    void activateTemplate_unknownTemplate_throwsResourceNotFound() {
        when(pipelineTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> pipelineService.activateTemplate(TEMPLATE_ID, hrAdmin))
                .isInstanceOf(ResourceNotFoundException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PIPELINE_TEMPLATE_NOT_FOUND);
    }

    @Test
    void activateTemplate_alreadyActive_isIdempotentNoOp() {
        PipelineTemplate template = templateWithDepartment(null);
        template.setStatus(PipelineTemplateStatus.ACTIVE);
        when(pipelineTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));

        PipelineTemplateResponseDto response = pipelineService.activateTemplate(TEMPLATE_ID, hrAdmin);

        assertThat(response.getStatus()).isEqualTo(PipelineTemplateStatus.ACTIVE);
        verify(pipelineTemplateRepository, never()).save(any());
        verify(pipelineStageRepository, never()).findByPipelineTemplate_IdAndActiveTrueOrderByPositionAsc(any());
    }

    @Test
    void activateTemplate_fewerThanTwoStages_throwsBusinessConflict_BR_PIPE_01() {
        PipelineTemplate template = templateWithDepartment(null);
        when(pipelineTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
        when(pipelineStageRepository.findByPipelineTemplate_IdAndActiveTrueOrderByPositionAsc(TEMPLATE_ID))
                .thenReturn(List.of(stageOfType(1L, 1, StageType.INTAKE, template)));

        assertThatThrownBy(() -> pipelineService.activateTemplate(TEMPLATE_ID, hrAdmin))
                .isInstanceOf(BusinessConflictException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PIPELINE_TEMPLATE_NOT_READY_TO_ACTIVATE);
        verify(pipelineTemplateRepository, never()).save(any());
    }

    @Test
    void activateTemplate_missingTerminalRejectedStage_throwsBusinessConflict_BR_PIPE_01() {
        PipelineTemplate template = templateWithDepartment(null);
        when(pipelineTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
        when(pipelineStageRepository.findByPipelineTemplate_IdAndActiveTrueOrderByPositionAsc(TEMPLATE_ID))
                .thenReturn(List.of(
                        stageOfType(1L, 1, StageType.INTAKE, template),
                        stageOfType(2L, 2, StageType.TERMINAL_SUCCESS, template)));

        assertThatThrownBy(() -> pipelineService.activateTemplate(TEMPLATE_ID, hrAdmin))
                .isInstanceOf(BusinessConflictException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PIPELINE_TEMPLATE_NOT_READY_TO_ACTIVATE);
    }

    @Test
    void activateTemplate_hasEnoughStagesAndBothTerminals_activatesSuccessfully() {
        PipelineTemplate template = templateWithDepartment(null);
        when(pipelineTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
        when(pipelineStageRepository.findByPipelineTemplate_IdAndActiveTrueOrderByPositionAsc(TEMPLATE_ID))
                .thenReturn(List.of(
                        stageOfType(1L, 1, StageType.INTAKE, template),
                        stageOfType(2L, 2, StageType.TERMINAL_SUCCESS, template),
                        stageOfType(3L, 3, StageType.TERMINAL_REJECTED, template)));

        PipelineTemplateResponseDto response = pipelineService.activateTemplate(TEMPLATE_ID, hrAdmin);

        assertThat(response.getStatus()).isEqualTo(PipelineTemplateStatus.ACTIVE);
        verify(pipelineTemplateRepository).save(template);
    }
}
