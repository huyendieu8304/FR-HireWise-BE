package com.hirewise.be.dto.response;

import com.hirewise.be.domain.PublishingChannelCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One row of the UC-31 share modal: a channel the Recruiter can send this Job
 * to, with both links already built server-side.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareTargetResponseDto {

    private PublishingChannelCode code;

    private String name;

    /**
     * The URL that actually gets shared - it points at HireWise's own Open
     * Graph landing page, not straight at the frontend, because that page is
     * what the LinkedIn/Facebook crawler can read and what counts clicks.
     */
    private String shareUrl;

    /**
     * The platform popup to open, with {@code shareUrl} already substituted
     * and URL-encoded. {@code null} for {@code COPY_LINK}, where the frontend
     * copies {@code shareUrl} to the clipboard instead of opening anything.
     */
    private String intentUrl;

    /** How many times this Job has already been shared to this channel. */
    private int shareCount;

    /** {@code null} if this Job has never been shared to this channel. */
    private Instant lastSharedAt;
}
