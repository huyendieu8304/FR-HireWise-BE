package com.hirewise.be.dto.response;

import com.hirewise.be.domain.PublishingChannelCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One row of the UC-19 "Cấu hình kênh chia sẻ" screen.
 *
 * <p>{@code shareIntentUrlTemplate} is deliberately exposed read-only: HR
 * Admin may switch a channel off or retune its {@code utmSource}, but the
 * intent URL is dictated by the platform and changing it would simply break
 * sharing, so it is shown for transparency and never edited.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishingChannelResponseDto {

    private PublishingChannelCode code;

    private String name;

    /** {@code null} for {@code COPY_LINK}, which the frontend handles with the clipboard. */
    private String shareIntentUrlTemplate;

    private String utmSource;

    private boolean enabled;

    private int displayOrder;
}
