package com.hirewise.be.dto.response;

import com.hirewise.be.domain.PublishingChannelCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * UC-32 - "Theo dõi hiệu quả chia sẻ tin tuyển dụng": how each channel has
 * performed for one Job.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobShareStatsResponseDto {

    /** One row per channel this Job has actually been shared to. */
    private List<ChannelStatRow> rows;

    /** Every application for this Job, whatever its source. */
    private long totalApplications;

    /**
     * Applications that arrived without a {@code utm_source} - candidates who
     * found the Job Board on their own. Reported separately rather than as a
     * channel row so the per-channel numbers stay honest.
     */
    private long directApplications;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChannelStatRow {

        private PublishingChannelCode code;

        private String name;

        /** Times a Recruiter pressed [Chia sẻ] for this channel. */
        private int shareCount;

        /** Times a real person opened the link; crawler fetches are excluded. */
        private int clickCount;

        /**
         * Applications whose {@code source} matches this channel's current
         * {@code utm_source}. Changing that value in UC-19 orphans the
         * applications recorded under the old one - by design, so past
         * attribution is never rewritten.
         */
        private long applicationCount;

        private Instant lastSharedAt;

        private String shareUrl;
    }
}
