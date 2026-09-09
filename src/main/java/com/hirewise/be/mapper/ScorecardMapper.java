package com.hirewise.be.mapper;

import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.JobStageScorecard;
import com.hirewise.be.domain.JobStageScorecardCriterion;
import com.hirewise.be.domain.PipelineStage;
import com.hirewise.be.domain.ScorecardCriterion;
import com.hirewise.be.domain.ScorecardScore;
import com.hirewise.be.domain.ScorecardSubmission;
import com.hirewise.be.domain.ScorecardTemplate;
import com.hirewise.be.dto.response.JobStageScorecardResponseDto;
import com.hirewise.be.dto.response.ScorecardCriterionResponseDto;
import com.hirewise.be.dto.response.ScorecardScoreResponseDto;
import com.hirewise.be.dto.response.ScorecardSubmissionResponseDto;
import com.hirewise.be.dto.response.ScorecardTemplateResponseDto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Converts {@code Scorecard*}/{@code JobStageScorecard*} entities into their
 * response DTOs (UC-27/UC-28). Pure conversion only - no repository queries
 * (same convention as {@link PipelineMapper}).
 */
public final class ScorecardMapper {

    private ScorecardMapper() {
    }

    public static ScorecardCriterionResponseDto toResponseDto(ScorecardCriterion entity) {
        return ScorecardCriterionResponseDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .weight(entity.getWeight())
                .maxScore(entity.getMaxScore())
                .position(entity.getPosition())
                .required(entity.isRequired())
                .build();
    }

    /** Same shape as {@link ScorecardCriterion} - see {@link JobStageScorecardCriterion}'s own Javadoc for why it's a separate entity. */
    public static ScorecardCriterionResponseDto toResponseDto(JobStageScorecardCriterion entity) {
        return ScorecardCriterionResponseDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .weight(entity.getWeight())
                .maxScore(entity.getMaxScore())
                .position(entity.getPosition())
                .required(entity.isRequired())
                .build();
    }

    /**
     * @param entity   Master Template entity to convert
     * @param criteria the template's criteria, already loaded by the caller
     *                 (ordered by position) - kept out of this mapper for the
     *                 same reason {@code PipelineMapper} takes
     *                 {@code applicationCount} as a parameter instead of
     *                 querying for it itself.
     */
    public static ScorecardTemplateResponseDto toResponseDto(ScorecardTemplate entity, List<ScorecardCriterion> criteria) {
        return ScorecardTemplateResponseDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .status(entity.getStatus())
                .criteria(criteria.stream().map(ScorecardMapper::toResponseDto).toList())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * @param entity   (Job, Stage) Scorecard entity to convert
     * @param criteria the scorecard's criteria, already loaded by the caller (ordered by position)
     */
    public static JobStageScorecardResponseDto toResponseDto(JobStageScorecard entity, List<JobStageScorecardCriterion> criteria) {
        JobPosition job = entity.getJob();
        PipelineStage stage = entity.getPipelineStage();
        ScorecardTemplate sourceMaster = entity.getSourceMasterTemplate();
        return JobStageScorecardResponseDto.builder()
                .id(entity.getId())
                .jobId(job.getId())
                .jobTitle(job.getTitle())
                .pipelineStageId(stage.getId())
                .stageName(stage.getName())
                .name(entity.getName())
                .version(entity.getVersion())
                .status(entity.getStatus())
                .sourceMasterTemplateId(sourceMaster != null ? sourceMaster.getId() : null)
                .sourceMasterTemplateName(sourceMaster != null ? sourceMaster.getName() : null)
                .criteria(criteria.stream().map(ScorecardMapper::toResponseDto).toList())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    /**
     * Merges the submission's saved scores with the FULL criteria list of its
     * (Job, Stage) Scorecard (in order) so the Scorecard Entry form always
     * shows every criterion, including ones never scored yet ({@code score = null}).
     *
     * @param submission          submission entity to convert
     * @param scorecardCriteria   every criterion of {@code submission.jobStageScorecard}, ordered by position
     * @param scoresByCriterionId this submission's saved {@link ScorecardScore} rows, keyed by criterion id
     */
    public static ScorecardSubmissionResponseDto toResponseDto(
            ScorecardSubmission submission,
            List<JobStageScorecardCriterion> scorecardCriteria,
            Map<UUID, ScorecardScore> scoresByCriterionId) {
        List<ScorecardScoreResponseDto> scoreRows = scorecardCriteria.stream()
                .map(criterion -> {
                    ScorecardScore existing = scoresByCriterionId.get(criterion.getId());
                    return ScorecardScoreResponseDto.builder()
                            .criterionId(criterion.getId())
                            .criterionName(criterion.getName())
                            .criterionDescription(criterion.getDescription())
                            .weight(criterion.getWeight())
                            .maxScore(criterion.getMaxScore())
                            .required(criterion.isRequired())
                            .score(existing != null ? existing.getScore() : null)
                            .comment(existing != null ? existing.getComment() : null)
                            .build();
                })
                .toList();

        PipelineStage stage = submission.getInterview().getPipelineStage();
        return ScorecardSubmissionResponseDto.builder()
                .submissionId(submission.getId())
                .interviewId(submission.getInterview().getId())
                .stageName(stage != null ? stage.getName() : null)
                .evaluatorId(submission.getEvaluator().getId())
                .evaluatorName(submission.getEvaluator().getFullName())
                .jobStageScorecardId(submission.getJobStageScorecard().getId())
                .jobStageScorecardName(submission.getJobStageScorecard().getName())
                .overallComment(submission.getOverallComment())
                .weightedScore(submission.getWeightedScore())
                .status(submission.getStatus())
                .submittedAt(submission.getSubmittedAt())
                .lockedAt(submission.getLockedAt())
                .scores(scoreRows)
                .build();
    }
}
