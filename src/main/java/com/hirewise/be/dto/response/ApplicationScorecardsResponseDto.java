package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * UC-28 step 6: the Applicant Card [Scorecard] tab - every Interview of this
 * Application grouped with each evaluator's result, plus
 * {@code averageWeightedScore} (average of every {@code SUBMITTED}
 * submission's {@code weighted_score} across the WHOLE Application, not just
 * 1 interview - the "diem trung binh co trong so" the Hiring Manager sees at
 * the top of the page per the SRS Normal Flow step 6).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationScorecardsResponseDto {
    /** {@code null} khi chưa có Scorecard nào ở trạng thái SUBMITTED. */
    private BigDecimal averageWeightedScore;
    private List<InterviewScorecardGroupDto> interviews;
}
