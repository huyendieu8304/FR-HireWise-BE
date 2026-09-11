package com.hirewise.be.dto.response;

import com.hirewise.be.domain.PublishingChannelCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * UC-42 - "Xem Dashboard báo cáo hiệu quả nguồn tuyển dụng (Source ROI)":
 * how every acquisition channel performed over the selected filter.
 *
 * <p><b>"ROI" here is funnel efficiency, not money.</b> Nothing in the system
 * records what a channel costs, so a channel is judged by how many of the
 * applications it brings actually turn into hires and how fast - never by
 * return over spend. The UI says so on the screen; do not add a cost column
 * without a real cost source behind it.</p>
 *
 * <p>An empty {@link #rows} list is a valid, successful result - the filter
 * simply matched nothing (EX-01, ME-37). It is not an error.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceRoiReportResponseDto {

    /** One row per distinct source, biggest first; may be empty (ME-37). */
    private List<SourceRow> rows;

    private long totalApplications;

    /** Applications in the cohort that reached a TERMINAL_SUCCESS stage. */
    private long totalHires;

    /** {@code totalHires / totalApplications}, in percent; {@code null} when there is no application. */
    private BigDecimal overallHireRate;

    /**
     * Label of the source with the best hire rate among those with at least
     * {@code MIN_SAMPLE_SIZE} applications. {@code null} when no source clears
     * that bar - a single 1-of-1 hire is not evidence of anything.
     */
    private String bestSourceLabel;

    private BigDecimal bestSourceHireRate;

    /** Mean days from applying to being hired, across the whole cohort. */
    private BigDecimal avgDaysToHire;

    /** Echo of the resolved filter, so an exported file explains itself. */
    private LocalDate fromDate;

    private LocalDate toDate;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceRow {

        /** Raw {@code utm_source} recorded on the application; empty for direct traffic. */
        private String sourceKey;

        /** {@code null} for direct traffic and for sources no channel claims. */
        private PublishingChannelCode channelCode;

        /** What the dashboard prints: the channel name, "Truy cập trực tiếp", or the raw key. */
        private String label;

        private long applicationCount;

        /** Share of the cohort, in percent - this is what the donut draws. */
        private BigDecimal applicationShare;

        private long hireCount;

        /** {@code hireCount / applicationCount} in percent; {@code null} when the source has no application. */
        private BigDecimal hireRate;

        /** Lifetime shares for this channel, NOT limited to the date range. */
        private long shareCount;

        /** Lifetime clicks for this channel, NOT limited to the date range. */
        private long clickCount;

        /**
         * Applications per 100 clicks. Mixes a windowed numerator with a
         * lifetime denominator, so it is a rough quality signal for the posting
         * text, not an exact conversion rate. {@code null} when there is no click.
         */
        private BigDecimal clickToApplyRate;

        /** Mean days to hire for this source; {@code null} until it produces a hire. */
        private BigDecimal avgDaysToHire;

        /** {@code true} for the row holding applications that arrived with no source. */
        private boolean direct;

        /** {@code true} when no configured channel claims this {@code utm_source} any more. */
        private boolean unknown;
    }
}
