package com.hirewise.be.domain;

/**
 * Lifecycle of a {@link ScorecardSubmission} (UC-28).
 */
public enum ScorecardSubmissionStatus {
    /** Evaluator has opened the form and/or saved progress; not all BR-SCORE-01 rules satisfied yet. */
    DRAFT,
    /** Evaluator pressed "Gui danh gia" - BR-SCORE-01 validated, weighted_score computed (BR-SCORE-02). */
    SUBMITTED
}
