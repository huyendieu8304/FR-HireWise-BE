package com.hirewise.be.domain;

/**
 * Shared lifecycle for both {@link ScorecardTemplate} (Master Template
 * library, UC-27) and {@link JobStageScorecard} (the real per-(Job, Stage)
 * scoring definition) - same 2 states, same meaning in both: {@code ACTIVE}
 * is currently offered/used, {@code ARCHIVED} is retired/superseded but the
 * row survives so whatever already references it keeps working unchanged.
 */
public enum ScorecardStatus {
    ACTIVE,
    ARCHIVED
}
