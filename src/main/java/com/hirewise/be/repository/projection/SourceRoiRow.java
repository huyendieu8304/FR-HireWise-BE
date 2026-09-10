package com.hirewise.be.repository.projection;

/**
 * UC-42: one aggregated row per acquisition source, as returned by
 * {@code ReportRepository#aggregateSourceRoi}. {@link #getSourceKey()} is the
 * raw {@code applications.source} (a {@code utm_source} string), with the
 * empty string standing in for {@code NULL} - candidates who reached the Job
 * Board directly.
 */
public interface SourceRoiRow {

    String getSourceKey();

    long getApplicationCount();

    /** Applications from this source that ever reached a TERMINAL_SUCCESS stage. */
    long getHireCount();

    /**
     * Mean days from {@code applied_at} to the first TERMINAL_SUCCESS event,
     * averaged over hired applications only. {@code null} when this source has
     * produced no hire yet.
     */
    Double getAvgDaysToHire();
}
