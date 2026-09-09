package com.hirewise.be.repository.projection;

/**
 * UC-43: one aggregated row per pipeline Stage, as returned by
 * {@code ReportRepository#aggregateStageVelocity}.
 *
 * <p>The unit of measurement is a <b>visit to a stage</b>, not an application.
 * An application rolled back into an earlier stage sits there twice and is
 * counted twice - which is correct, because it really did consume that stage
 * twice.</p>
 *
 * <p>A visit is <i>completed</i> once the application moves on. Averages,
 * median and P90 cover completed visits only: a visit still in progress has no
 * end yet, and folding a partial duration into the mean would drag it down
 * exactly when a stage is at its most stuck. {@link #getWaitingCount()} and
 * {@link #getMaxWaitingDays()} carry that in-progress picture separately.</p>
 */
public interface StageVelocityRow {

    /** Stage code, the key stages are grouped by across pipeline templates. */
    String getStageCode();

    String getStageName();

    String getStageType();

    /** Position within the pipeline, used to keep the chart in pipeline order. */
    int getStageOrder();

    /** Configured SLA in hours, or {@code null} when the stage has no target. */
    Integer getSlaHours();

    /** Completed visits - the sample behind avg, median and P90. */
    long getCompletedCount();

    Double getAvgDays();

    Double getMedianDays();

    Double getP90Days();

    /** Every visit to this stage, finished or not. */
    long getEnteredCount();

    /** Visits that moved on to any stage other than a rejection. */
    long getAdvancedCount();

    /** Visits that ended in a TERMINAL_REJECTED stage. */
    long getRejectedCount();

    /** Applications sitting in this stage right now. */
    long getWaitingCount();

    /** Longest wait among those, in days; {@code null} when nobody is waiting. */
    Double getMaxWaitingDays();
}
