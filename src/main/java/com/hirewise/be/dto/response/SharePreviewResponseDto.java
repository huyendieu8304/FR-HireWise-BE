package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UC-31 screen field 2, "Preview định dạng bài đăng" - exactly the three
 * values the Open Graph landing page will publish, so the Recruiter sees the
 * real card before sharing rather than a guess.
 *
 * <p>Worth knowing when reading the share modal: both LinkedIn's
 * {@code share-offsite} and Facebook's {@code sharer.php} ignore any text
 * passed to them and build the card purely from these Open Graph tags. There
 * is therefore no caption to preview - this <i>is</i> the whole post.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharePreviewResponseDto {

    /** {@code og:title} - the job title. */
    private String title;

    /** {@code og:description} - the JD, trimmed to a card-sized excerpt. */
    private String description;

    /** {@code og:image} - the company-wide default card image. */
    private String imageUrl;

    /** Host shown under the card by both platforms. */
    private String siteName;
}
