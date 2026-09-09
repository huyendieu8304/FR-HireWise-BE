package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 1 criterion's row for the Scorecard Entry form - merges the criterion's own
 * definition (name/weight/max_score/required, read-only) with this
 * submission's current rating (score/comment, editable) so the FE renders
 * the whole "Interactive Star Rating" row from a single object.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScorecardScoreResponseDto {
    private UUID criterionId;
    private String criterionName;
    private String criterionDescription;
    private BigDecimal weight;
    private BigDecimal maxScore;
    private boolean required;
    /** {@code null} = chưa chấm điểm tiêu chí này. */
    private BigDecimal score;
    private String comment;
}
