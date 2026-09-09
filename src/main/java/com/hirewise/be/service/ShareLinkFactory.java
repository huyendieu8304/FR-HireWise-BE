package com.hirewise.be.service;

import com.hirewise.be.domain.PublishingChannel;
import com.hirewise.be.domain.PublishingChannelCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * The single place every share URL is built (UC-31/UC-32).
 *
 * <p>Three different callers need these strings - the share modal, the Open
 * Graph landing page, and the stats panel - and they must agree exactly, since
 * a link already posted to LinkedIn cannot be corrected afterwards. Keeping
 * the shapes here rather than inlining them means the UTM parameter names and
 * the {@code /j/} prefix are stated once.</p>
 */
@Component
public class ShareLinkFactory {

    /** Substituted into {@link PublishingChannel#getShareIntentUrlTemplate()}. */
    private static final String URL_PLACEHOLDER = "{url}";

    /** Open Graph descriptions are truncated by every platform anyway; this keeps the preview honest. */
    private static final int OG_DESCRIPTION_MAX_LENGTH = 200;

    private final String publicBaseUrl;
    private final String frontendBaseUrl;
    private final String ogImageUrl;

    public ShareLinkFactory(
            @Value("${app.share.public-base-url}") String publicBaseUrl,
            @Value("${app.share.frontend-base-url}") String frontendBaseUrl,
            @Value("${app.share.og-image-url}") String ogImageUrl) {
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
        this.frontendBaseUrl = stripTrailingSlash(frontendBaseUrl);
        this.ogImageUrl = ogImageUrl;
    }

    /**
     * The URL that actually gets posted to the external platform.
     *
     * <p>It points at HireWise's own backend, not at the React app, for two
     * reasons that both matter: the frontend is a client-rendered Vite SPA, so
     * a crawler fetching it sees an empty {@code <div id="root">} and no Open
     * Graph tags at all; and routing the click through our own server is what
     * makes {@code click_count} possible in the first place.</p>
     *
     * @param jobId the job being shared
     * @param code  which channel the link is being handed to
     * @return an absolute URL pointing at the Open Graph landing page
     */
    public String shareUrl(UUID jobId, PublishingChannelCode code) {
        return publicBaseUrl + "/j/" + jobId + "?ch=" + code.name();
    }

    /**
     * The platform popup to open, or {@code null} when the channel has no
     * template ({@code COPY_LINK}).
     *
     * @param channel  the channel, carrying its intent URL template
     * @param shareUrl the already-built share URL, encoded before substitution
     * @return the full share-intent URL, or {@code null} for a copy-link channel
     */
    public String intentUrl(PublishingChannel channel, String shareUrl) {
        String template = channel.getShareIntentUrlTemplate();
        if (template == null || template.isBlank()) {
            return null;
        }
        return template.replace(URL_PLACEHOLDER, UriUtils.encode(shareUrl, StandardCharsets.UTF_8));
    }

    /**
     * Where the landing page finally sends a real visitor: the public apply
     * page, carrying the UTM parameters that {@code ApplyPage} reads and
     * echoes back as {@code applications.source} (UC-32 attribution).
     *
     * @param jobId     the job being visited
     * @param utmSource the channel's configured UTM source, e.g. {@code linkedin}
     * @return an absolute frontend URL with UTM parameters appended
     */
    public String landingUrl(UUID jobId, String utmSource) {
        StringBuilder url = new StringBuilder(frontendBaseUrl)
                .append("/careers/").append(jobId).append("/apply");
        if (utmSource != null && !utmSource.isBlank()) {
            url.append("?utm_source=").append(UriUtils.encode(utmSource, StandardCharsets.UTF_8))
                    .append("&utm_medium=social")
                    .append("&utm_campaign=job_").append(jobId);
        }
        return url.toString();
    }

    /**
     * The internal Job detail page. EM-10 goes to the Recruiter, not to a
     * candidate, so its "xem chi tiết" link has to land on the Job they manage
     * rather than on the public apply page.
     *
     * @param jobId the job to link to
     * @return an absolute frontend URL for the internal Job detail screen
     */
    public String jobDetailUrl(UUID jobId) {
        return frontendBaseUrl + "/jobs/" + jobId;
    }

    /**
     * Trims a job description down to an {@code og:description}, cutting on a
     * word boundary so the preview never ends mid-word.
     *
     * <p>Lives here rather than in {@code JobShareService} because both the
     * share-modal preview and the landing page's real Open Graph tag must
     * produce identical text - a preview that does not match what actually
     * gets posted is worse than no preview at all.</p>
     *
     * @param description the job description, possibly null or multi-line
     * @return a single-line excerpt, never null
     */
    public String ogDescription(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        String flattened = description.replaceAll("\\s+", " ").trim();
        if (flattened.length() <= OG_DESCRIPTION_MAX_LENGTH) {
            return flattened;
        }
        String cut = flattened.substring(0, OG_DESCRIPTION_MAX_LENGTH);
        int lastSpace = cut.lastIndexOf(' ');
        if (lastSpace > 0) {
            cut = cut.substring(0, lastSpace);
        }
        return cut + "...";
    }

    /** @return the absolute {@code og:image} URL used for every job card */
    public String ogImageUrl() {
        return ogImageUrl;
    }

    /** @return the public base URL, shown as the card's site name */
    public String publicBaseUrl() {
        return publicBaseUrl;
    }

    private static String stripTrailingSlash(String value) {
        if (value != null && value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
