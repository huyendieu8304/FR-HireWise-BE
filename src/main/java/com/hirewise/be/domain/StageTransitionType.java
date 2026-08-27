package com.hirewise.be.domain;

/** How an {@link ApplicationStageHistory} row was created (LV-12). */
public enum StageTransitionType {
    MANUAL,
    SYSTEM,
    ROLLBACK
}
