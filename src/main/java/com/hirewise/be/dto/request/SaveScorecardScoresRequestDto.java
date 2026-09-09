package com.hirewise.be.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request body for saving progress on a Scorecard Submission (UC-28 step 2-3,
 * {@code PUT /scorecard-submissions/{id}}) - does NOT submit/finalize; that
 * is a separate {@code POST .../submit} call so partial progress can be
 * saved any number of times before the evaluator is ready (BR-SCORE-01 is
 * validated only at submit time, not here).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SaveScorecardScoresRequestDto {

    private String overallComment;

    @NotNull(message = "Thiếu danh sách điểm.")
    @Valid
    private List<ScorecardScoreInputDto> scores;
}
