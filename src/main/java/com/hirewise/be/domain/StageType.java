package com.hirewise.be.domain;

/**
 * Category of a {@link PipelineStage} (LV-06). Purely descriptive/reporting
 * metadata except for the two {@code TERMINAL_*} values, which BR-PIPE-01
 * requires at least one of each per {@link PipelineTemplate} and which imply
 * {@code is_terminal = true}.
 */
public enum StageType {
    INTAKE,
    SCREENING,
    INTERVIEW,
    OFFER,
    TERMINAL_SUCCESS,
    TERMINAL_REJECTED
}
