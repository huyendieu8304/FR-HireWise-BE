package com.hirewise.be.dto.response;

import com.hirewise.be.domain.ScorecardSubmissionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Read-only row for the Applicant Card [Scorecard] tab - 1 evaluator's result for 1 Interview. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScorecardSubmissionSummaryDto {
    private UUID submissionId;
    private Long evaluatorId;
    private String evaluatorName;
    private ScorecardSubmissionStatus status;
    private BigDecimal weightedScore;
    private Instant submittedAt;
    private boolean locked;
    /** Whether the caller viewing this list IS this evaluator - FE uses it to show "Chấm điểm"/"Sửa" vs read-only. */
    private boolean currentUser;
}
