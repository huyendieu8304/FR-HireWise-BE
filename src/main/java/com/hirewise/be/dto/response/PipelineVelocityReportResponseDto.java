package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * UC-43 - "Xem Dashboard báo cáo tốc độ chuyển đổi giữa các Stage (Pipeline
 * Velocity)": how long applications sit in each Stage, so a bottleneck can be
 * named instead of guessed at.
 *
 * <p>The bar chart draws {@link StageRow#getAvgDays()}, exactly as the screen
 * specification asks. Everything alongside it is there because the average on
 * its own is not enough to act on:</p>
 * <ul>
 *   <li>the <b>median</b> next to the mean says whether a stage is slow for
 *       everyone or slow because of a handful of forgotten applications - two
 *       problems with two different fixes;</li>
 *   <li><b>P90</b> is the experience of the candidates most likely to walk away;</li>
 *   <li><b>completedCount</b> is the sample size, so a two-application stage is
 *       not mistaken for a trend;</li>
 *   <li><b>waitingCount</b> covers what the average structurally cannot see -
 *       applications still stuck in the stage right now;</li>
 *   <li><b>passThroughRate</b> catches the other kind of bottleneck: a stage
 *       that is quick but rejects almost everyone.</li>
 * </ul>
 *
 * <p>An empty {@link #stages} list is a valid, successful result (EX-01, ME-37).</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineVelocityReportResponseDto {

    /** Non-terminal stages in pipeline order; may be empty (ME-37). */
    private List<StageRow> stages;

    /** Mean days from applying to being hired; {@code null} when nobody was hired. */
    private BigDecimal avgTimeToHireDays;

    private long hiredCount;

    /** Code of the stage flagged as the bottleneck, or {@code null} when none qualifies. */
    private String bottleneckStageCode;

    /** Echo of the resolved filter, so an exported file explains itself. */
    private LocalDate fromDate;

    private LocalDate toDate;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageRow {

        /** Grouping key across Pipeline Templates. */
        private String stageCode;

        private String stageName;

        /** INTAKE, SCREENING, INTERVIEW or OFFER - terminal stages never appear. */
        private String stageType;

        private int stageOrder;

        /** Mean days over completed visits; {@code null} when none has completed. */
        private BigDecimal avgDays;

        private BigDecimal medianDays;

        private BigDecimal p90Days;

        /** Completed visits, i.e. the sample behind the three figures above. */
        private long completedCount;

        /** Configured target from {@code pipeline_stages.sla_hours}, in days; {@code null} when unset. */
        private BigDecimal slaDays;

        /** {@code true} when the average exceeds the configured target. */
        private boolean slaBreached;

        /**
         * {@code true} for the one stage the dashboard highlights. An SLA breach
         * wins outright; only when no stage breaches does the slowest
         * sufficiently-sampled stage take the flag.
         */
        private boolean bottleneck;

        private long enteredCount;

        private long advancedCount;

        private long rejectedCount;

        /** Advanced over decided visits, in percent; {@code null} while nothing is decided. */
        private BigDecimal passThroughRate;

        /** Applications sitting in this stage right now. */
        private long waitingCount;

        /** Longest current wait, in days; {@code null} when nobody is waiting. */
        private BigDecimal maxWaitingDays;
    }
}
