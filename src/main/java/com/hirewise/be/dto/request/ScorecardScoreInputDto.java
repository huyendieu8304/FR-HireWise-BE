package com.hirewise.be.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One criterion's rating within {@link SaveScorecardScoresRequestDto} (UC-28
 * step 2). {@code score}/{@code comment} may both be {@code null} while
 * saving progress (DRAFT) - only {@code submit} (a separate endpoint)
 * enforces BR-SCORE-01's "every required criterion must be filled in" rule.
 * <p>
 * The upper bound (a criterion's own {@code max_score}) is NOT expressible
 * here since it varies per criterion - {@code ScorecardSubmissionService}
 * checks it against the actual criterion looked up server-side.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScorecardScoreInputDto {

    @NotNull(message = "Thiếu criterionId.")
    private UUID criterionId;

    @DecimalMin(value = "0", message = "Điểm không được âm.")
    private BigDecimal score;

    private String comment;
}
