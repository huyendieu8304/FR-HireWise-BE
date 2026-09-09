package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.Department;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.JobStageScorecard;
import com.hirewise.be.domain.PipelineStage;
import com.hirewise.be.domain.PipelineTemplate;
import com.hirewise.be.domain.ScorecardStatus;
import com.hirewise.be.domain.ScorecardTemplate;
import com.hirewise.be.domain.StageType;
import com.hirewise.be.dto.request.ScorecardCriterionInputDto;
import com.hirewise.be.dto.request.SaveJobStageScorecardRequestDto;
import com.hirewise.be.dto.response.InterviewStageScorecardStatusDto;
import com.hirewise.be.dto.response.JobStageScorecardResponseDto;
import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.repository.JobPositionRepository;
import com.hirewise.be.repository.JobStageScorecardCriterionRepository;
import com.hirewise.be.repository.JobStageScorecardRepository;
import com.hirewise.be.repository.PipelineStageRepository;
import com.hirewise.be.repository.ScorecardSubmissionRepository;
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
 * UC-27 step 3 + UC-14/15 hard gate: the REAL per-(Job, Stage) Scorecard -
 * scoped strictly to 1 (Job, Interview-type Stage) pair, versioned (AF-01)
 * once it has real grading history - unlike the (now purely reference-only)
 * Master Template library covered by {@link ScorecardTemplateServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class JobStageScorecardServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-08T00:00:00Z");
    private static final UUID JOB_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Long DEPARTMENT_ID = 4L;
    private static final Long PIPELINE_TEMPLATE_ID = 7L;
    private static final Long STAGE_ID = 10L;
    private static final UUID SCORECARD_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Mock private JobStageScorecardRepository jobStageScorecardRepository;
    @Mock private JobStageScorecardCriterionRepository jobStageScorecardCriterionRepository;
    @Mock private ScorecardSubmissionRepository scorecardSubmissionRepository;
    @Mock private ScorecardTemplateRepository scorecardTemplateRepository;
    @Mock private JobPositionRepository jobPositionRepository;
    @Mock private PipelineStageRepository pipelineStageRepository;
    @Mock private AccessControlService accessControlService;

    private JobStageScorecardService service;
    private final CurrentUser currentUser = new CurrentUser(1L, "hm@test.com", "Hiring Manager", Set.of("HIRING_MANAGER"));

    private JobPosition job;
    private PipelineStage interviewStage;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new JobStageScorecardService(jobStageScorecardRepository, jobStageScorecardCriterionRepository,
                scorecardSubmissionRepository, scorecardTemplateRepository, jobPositionRepository,
                pipelineStageRepository, accessControlService, clock);

        PipelineTemplate pipelineTemplate = PipelineTemplate.builder().id(PIPELINE_TEMPLATE_ID).build();
        Department department = Department.builder().id(DEPARTMENT_ID).build();
        job = JobPosition.builder().id(JOB_ID).department(department).pipelineTemplate(pipelineTemplate).build();
        interviewStage = PipelineStage.builder().id(STAGE_ID).pipelineTemplate(pipelineTemplate)
                .name("Technical Interview").stageType(StageType.INTERVIEW).position(3).active(true).build();
        // Not stubbed here (would be an unused stub for getStageStatusForJob tests, which
        // take the JobPosition directly and never look it up by id) - each test that
        // actually goes through saveForJobStage/getForJobStage stubs it itself.
    }

    private ScorecardCriterionInputDto criterion(String name, String weight, String maxScore, boolean required) {
        ScorecardCriterionInputDto dto = new ScorecardCriterionInputDto();
        dto.setName(name);
        dto.setWeight(new BigDecimal(weight));
        dto.setMaxScore(new BigDecimal(maxScore));
        dto.setRequired(required);
        return dto;
    }

    private SaveJobStageScorecardRequestDto request(UUID sourceMasterTemplateId, ScorecardCriterionInputDto... criteria) {
        return new SaveJobStageScorecardRequestDto("Technical Interview - Backend", sourceMasterTemplateId, List.of(criteria));
    }

    @Test
    void save_totalWeightZero_throwsBadRequest() {
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(pipelineStageRepository.findById(STAGE_ID)).thenReturn(Optional.of(interviewStage));

        assertThatThrownBy(() -> service.saveForJobStage(JOB_ID, STAGE_ID, request(null, criterion("A", "0", "5", true)), currentUser))
                .isInstanceOf(BadRequestException.class);
        verify(jobStageScorecardRepository, never()).save(any());
    }

    @Test
    void save_stageNotInterviewType_throwsBadRequest() {
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        PipelineStage screeningStage = PipelineStage.builder().id(STAGE_ID)
                .pipelineTemplate(job.getPipelineTemplate()).stageType(StageType.SCREENING).active(true).build();
        when(pipelineStageRepository.findById(STAGE_ID)).thenReturn(Optional.of(screeningStage));

        assertThatThrownBy(() -> service.saveForJobStage(JOB_ID, STAGE_ID, request(null, criterion("A", "1", "5", true)), currentUser))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void save_stageBelongsToDifferentPipelineTemplate_throwsBadRequest() {
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        PipelineTemplate otherTemplate = PipelineTemplate.builder().id(99L).build();
        PipelineStage foreignStage = PipelineStage.builder().id(STAGE_ID)
                .pipelineTemplate(otherTemplate).stageType(StageType.INTERVIEW).active(true).build();
        when(pipelineStageRepository.findById(STAGE_ID)).thenReturn(Optional.of(foreignStage));

        assertThatThrownBy(() -> service.saveForJobStage(JOB_ID, STAGE_ID, request(null, criterion("A", "1", "5", true)), currentUser))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void save_noneExistsYet_createsVersion1AndChecksAccessScopedToJobDepartment() {
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(pipelineStageRepository.findById(STAGE_ID)).thenReturn(Optional.of(interviewStage));
        when(jobStageScorecardRepository.findFirstByJob_IdAndPipelineStage_IdAndStatusOrderByVersionDesc(
                JOB_ID, STAGE_ID, ScorecardStatus.ACTIVE)).thenReturn(Optional.empty());

        JobStageScorecardResponseDto result = service.saveForJobStage(
                JOB_ID, STAGE_ID, request(null, criterion("Technical depth", "100", "5", true)), currentUser);

        verify(accessControlService).checkAccess(eq(currentUser), eq(PermissionCodes.SCORECARD_TEMPLATE_MANAGE),
                eq(ResourceContext.job(JOB_ID, DEPARTMENT_ID)));
        ArgumentCaptor<JobStageScorecard> captor = ArgumentCaptor.forClass(JobStageScorecard.class);
        verify(jobStageScorecardRepository).save(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(1);
        assertThat(captor.getValue().getStatus()).isEqualTo(ScorecardStatus.ACTIVE);
        assertThat(result.getStageName()).isEqualTo("Technical Interview");
    }

    @Test
    void save_clonedFromMasterTemplate_recordsSourceForTraceabilityOnly() {
        UUID masterId = UUID.randomUUID();
        ScorecardTemplate master = ScorecardTemplate.builder().id(masterId).name("Technical - Backend")
                .status(ScorecardStatus.ACTIVE).createdAt(NOW).updatedAt(NOW).build();
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(pipelineStageRepository.findById(STAGE_ID)).thenReturn(Optional.of(interviewStage));
        when(scorecardTemplateRepository.findById(masterId)).thenReturn(Optional.of(master));
        when(jobStageScorecardRepository.findFirstByJob_IdAndPipelineStage_IdAndStatusOrderByVersionDesc(
                JOB_ID, STAGE_ID, ScorecardStatus.ACTIVE)).thenReturn(Optional.empty());

        JobStageScorecardResponseDto result = service.saveForJobStage(
                JOB_ID, STAGE_ID, request(masterId, criterion("A", "1", "5", true)), currentUser);

        assertThat(result.getSourceMasterTemplateId()).isEqualTo(masterId);
        assertThat(result.getSourceMasterTemplateName()).isEqualTo("Technical - Backend");
    }

    @Test
    void save_sourceMasterTemplateNotFound_throwsResourceNotFound() {
        UUID masterId = UUID.randomUUID();
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(pipelineStageRepository.findById(STAGE_ID)).thenReturn(Optional.of(interviewStage));
        when(scorecardTemplateRepository.findById(masterId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.saveForJobStage(JOB_ID, STAGE_ID, request(masterId, criterion("A", "1", "5", true)), currentUser))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void save_neverGraded_editsInPlaceKeepingVersion() {
        JobStageScorecard existing = JobStageScorecard.builder()
                .id(SCORECARD_ID).job(job).pipelineStage(interviewStage).name("Old name")
                .version(1).status(ScorecardStatus.ACTIVE).createdAt(NOW).updatedAt(NOW).build();
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(pipelineStageRepository.findById(STAGE_ID)).thenReturn(Optional.of(interviewStage));
        when(jobStageScorecardRepository.findFirstByJob_IdAndPipelineStage_IdAndStatusOrderByVersionDesc(
                JOB_ID, STAGE_ID, ScorecardStatus.ACTIVE)).thenReturn(Optional.of(existing));
        when(scorecardSubmissionRepository.existsByJobStageScorecard_Id(SCORECARD_ID)).thenReturn(false);

        JobStageScorecardResponseDto result = service.saveForJobStage(
                JOB_ID, STAGE_ID, request(null, criterion("New criterion", "100", "5", true)), currentUser);

        assertThat(result.getId()).isEqualTo(SCORECARD_ID);
        assertThat(existing.getName()).isEqualTo("Technical Interview - Backend");
        assertThat(existing.getVersion()).isEqualTo(1);
        assertThat(existing.getStatus()).isEqualTo(ScorecardStatus.ACTIVE);
        verify(jobStageScorecardCriterionRepository).deleteByJobStageScorecard_Id(SCORECARD_ID);
    }

    @Test
    void save_alreadyGraded_archivesOldAndCreatesNewVersion() {
        JobStageScorecard existing = JobStageScorecard.builder()
                .id(SCORECARD_ID).job(job).pipelineStage(interviewStage).name("Old name")
                .version(1).status(ScorecardStatus.ACTIVE).createdAt(NOW).updatedAt(NOW).build();
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(pipelineStageRepository.findById(STAGE_ID)).thenReturn(Optional.of(interviewStage));
        when(jobStageScorecardRepository.findFirstByJob_IdAndPipelineStage_IdAndStatusOrderByVersionDesc(
                JOB_ID, STAGE_ID, ScorecardStatus.ACTIVE)).thenReturn(Optional.of(existing));
        when(scorecardSubmissionRepository.existsByJobStageScorecard_Id(SCORECARD_ID)).thenReturn(true);

        JobStageScorecardResponseDto result = service.saveForJobStage(
                JOB_ID, STAGE_ID, request(null, criterion("Revised", "100", "5", true)), currentUser);

        // Old version archived, never mutated - existing submissions that reference
        // SCORECARD_ID keep seeing exactly what they were graded against.
        assertThat(existing.getStatus()).isEqualTo(ScorecardStatus.ARCHIVED);
        assertThat(existing.getName()).isEqualTo("Old name");
        verify(jobStageScorecardCriterionRepository, never()).deleteByJobStageScorecard_Id(any());

        assertThat(result.getId()).isNotEqualTo(SCORECARD_ID);
        assertThat(result.getVersion()).isEqualTo(2);
        assertThat(result.getStatus()).isEqualTo(ScorecardStatus.ACTIVE);
    }

    @Test
    void getForJobStage_notConfiguredYet_throwsResourceNotFound() {
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(jobStageScorecardRepository.findFirstByJob_IdAndPipelineStage_IdAndStatusOrderByVersionDesc(
                JOB_ID, STAGE_ID, ScorecardStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getForJobStage(JOB_ID, STAGE_ID, currentUser))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getStageStatusForJob_mixOfConfiguredAndMissing() {
        PipelineStage phoneScreen = PipelineStage.builder().id(20L).pipelineTemplate(job.getPipelineTemplate())
                .name("Phone Screen").stageType(StageType.INTERVIEW).position(2).active(true).build();
        PipelineStage finalRound = PipelineStage.builder().id(30L).pipelineTemplate(job.getPipelineTemplate())
                .name("Final Interview").stageType(StageType.INTERVIEW).position(4).active(true).build();
        PipelineStage screeningStage = PipelineStage.builder().id(15L).pipelineTemplate(job.getPipelineTemplate())
                .name("Qualification").stageType(StageType.SCREENING).position(1).active(true).build();

        when(pipelineStageRepository.findByPipelineTemplate_IdAndActiveTrueOrderByPositionAsc(PIPELINE_TEMPLATE_ID))
                .thenReturn(List.of(screeningStage, phoneScreen, interviewStage, finalRound));

        JobStageScorecard configuredForPhoneScreen = JobStageScorecard.builder()
                .id(UUID.randomUUID()).job(job).pipelineStage(phoneScreen).status(ScorecardStatus.ACTIVE).build();
        when(jobStageScorecardRepository.findByJob_IdAndStatus(JOB_ID, ScorecardStatus.ACTIVE))
                .thenReturn(List.of(configuredForPhoneScreen));

        List<InterviewStageScorecardStatusDto> result = service.getStageStatusForJob(job);

        // Only the 3 INTERVIEW-type stages appear - SCREENING is excluded entirely.
        assertThat(result).hasSize(3);
        assertThat(result).extracting(InterviewStageScorecardStatusDto::getStageName)
                .containsExactly("Phone Screen", "Technical Interview", "Final Interview");
        assertThat(result.get(0).isConfigured()).isTrue();
        assertThat(result.get(1).isConfigured()).isFalse();
        assertThat(result.get(2).isConfigured()).isFalse();
    }

    @Test
    void getStageStatusForJob_noPipelineTemplateAssigned_returnsEmpty() {
        JobPosition jobWithoutPipeline = JobPosition.builder().id(JOB_ID).build();
        assertThat(service.getStageStatusForJob(jobWithoutPipeline)).isEmpty();
    }
}
