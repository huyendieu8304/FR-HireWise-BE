package com.hirewise.be.domain;

/**
 * Lifecycle status of a {@link PipelineTemplate} (LV in SRS section 5.4.3).
 * <ul>
 *   <li>{@code DRAFT} - being configured; cannot yet be assigned to a Job
 *       Position (UC-04 EX-02) because BR-PIPE-01 is not satisfied (needs at
 *       least 2 stages, including one {@code TERMINAL_SUCCESS} and one
 *       {@code TERMINAL_REJECTED} stage).</li>
 *   <li>{@code ACTIVE} - BR-PIPE-01 satisfied; selectable in UC-13.</li>
 * </ul>
 */
public enum PipelineTemplateStatus {
    DRAFT,
    ACTIVE
}
