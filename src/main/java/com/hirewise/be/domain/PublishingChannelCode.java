package com.hirewise.be.domain;

import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.ErrorCode;

/**
 * The external destinations a Job Position can be shared to (UC-19/UC-31).
 * Mirrors the {@code chk_publishing_channels_code} constraint in {@code V39}.
 *
 * <p>{@link #COPY_LINK} is not a social network at all - it is the "just give
 * me the URL" option, which still needs a row here so that clicks arriving
 * from a hand-pasted link get attributed rather than counted as direct
 * traffic (UC-32).</p>
 */
public enum PublishingChannelCode {
    LINKEDIN,
    FACEBOOK,
    /** Formerly Twitter; the share intent host is still {@code twitter.com}. */
    X,
    COPY_LINK;

    /**
     * Parses the value used in {@code /api/jobs/{jobId}/share-channels/{code}}
     * URLs. Accepts the enum name in any case, so both {@code LINKEDIN} and
     * {@code linkedin} work.
     *
     * @param value the {@code {channelCode}} path variable
     * @return the matching channel
     * @throws BadRequestException if {@code value} is not a known channel
     */
    public static PublishingChannelCode from(String value) {
        if (value != null) {
            for (PublishingChannelCode code : values()) {
                if (code.name().equalsIgnoreCase(value)) {
                    return code;
                }
            }
        }
        throw new BadRequestException(ErrorCode.PUBLISHING_CHANNEL_NOT_FOUND, value);
    }
}
