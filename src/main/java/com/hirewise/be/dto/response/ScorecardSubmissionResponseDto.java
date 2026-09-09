package com.hirewise.be.dto.response;

import com.hirewise.be.domain.ScorecardSubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * UC-28: the Scorecard Entry form for 1 Interview - the current user's own
 * submission (auto-created as {@code DRAFT} on first open, see
 * {@code ScorecardSubmissionService#getOrCreateDraft}) plus every criterion
 * of the resolved (Job, Stage) Scorecard, merged for direct rendering.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScorecardSubmissionResponseDto {
    private UUID submissionId;
    private UUID interviewId;
    /** Tên Stage phỏng vấn (UC-28 req 5) - vd "Technical Interview". */
    private String stageName;
    private Long evaluatorId;
    private String evaluatorName;
    private UUID jobStageScorecardId;
    private String jobStageScorecardName;
    private String overallComment;
    /** {@code null} cho tới khi Submit (BR-SCORE-02). */
    private BigDecimal weightedScore;
    private ScorecardSubmissionStatus status;
    private Instant submittedAt;
    /** Khác {@code null} = đã bị khoá (BR-SCORE-03) - FE hiển thị read-only. */
    private Instant lockedAt;
    private List<ScorecardScoreResponseDto> scores;
}
