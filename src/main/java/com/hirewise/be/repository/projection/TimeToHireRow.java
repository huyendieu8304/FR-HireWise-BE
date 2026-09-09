package com.hirewise.be.repository.projection;

/**
 * UC-43: the headline number of the Pipeline Velocity dashboard - how long the
 * whole pipeline takes end to end, against which the per-stage bars are read.
 */
public interface TimeToHireRow {

    /** Applications in the cohort that reached a TERMINAL_SUCCESS stage. */
    long getHiredCount();

    /** Mean days from applying to being hired; {@code null} when nobody was hired. */
    Double getAvgDaysToHire();
}
