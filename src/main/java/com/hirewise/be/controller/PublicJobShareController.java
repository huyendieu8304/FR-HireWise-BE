package com.hirewise.be.controller;

import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.JobStatus;
import com.hirewise.be.domain.PublishingChannel;
import com.hirewise.be.domain.PublishingChannelCode;
import com.hirewise.be.repository.JobPositionRepository;
import com.hirewise.be.repository.PublishingChannelRepository;
import com.hirewise.be.service.JobShareService;
import com.hirewise.be.service.ShareLinkFactory;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The landing page every share link points at (UC-31/UC-32). Anonymous, HTML,
 * and deliberately outside {@code /api} - it is a page for a browser and a
 * crawler, not a JSON endpoint for the frontend.
 *
 * <p><b>Why this exists at all.</b> The React app is a client-rendered Vite
 * SPA. Facebook's {@code facebookexternalhit} and LinkedIn's
 * {@code LinkedInBot} do not execute JavaScript, so any {@code og:} tag set
 * from React is invisible to them and the shared post renders as a bare link
 * with no title, description or image. Serving these tags from Spring is the
 * only way to get a real preview card.</p>
 *
 * <p>The page does two jobs in one request: it publishes the Open Graph tags
 * for crawlers, and it counts the visit and forwards real people on to the
 * apply page with UTM parameters attached.</p>
 *
 * <p>The HTML is assembled as a string rather than through a view engine
 * because the project has no template engine on the classpath (no Thymeleaf,
 * and {@code resources/templates} is empty), and adding one for a single
 * 30-line page would be a new dependency for no benefit. Every interpolated
 * value goes through {@link HtmlUtils#htmlEscape}.</p>
 */
@Slf4j
@RestController
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class PublicJobShareController {

    /**
     * User agents that fetch the page only to build a link preview. They must
     * not inflate {@code click_count} - Facebook and LinkedIn both crawl the
     * moment a link is pasted, so counting them would mean every share
     * registered a phantom visitor.
     */
    private static final Pattern CRAWLER_USER_AGENT = Pattern.compile(
            "facebookexternalhit|facebookcatalog|LinkedInBot|Twitterbot|Slackbot|"
                    + "WhatsApp|TelegramBot|Discordbot|SkypeUriPreview|Applebot|"
                    + "Googlebot|bingbot|redditbot|Pinterest",
            Pattern.CASE_INSENSITIVE);

    JobPositionRepository jobPositionRepository;
    PublishingChannelRepository publishingChannelRepository;
    JobShareService jobShareService;
    ShareLinkFactory shareLinkFactory;

    /**
     * Serves the Open Graph card for a shared Job and forwards real visitors
     * to the apply page.
     *
     * <p>Always returns HTML, never JSON - an unknown or no-longer-Published
     * job gets a small "tin tuyển dụng không còn khả dụng" page rather than
     * the API error envelope, because this URL is opened directly by people
     * clicking a link on LinkedIn.</p>
     *
     * @param jobId       the shared job
     * @param channelCode the {@code ch} parameter naming which channel the
     *                    link was handed to; absent or unknown means the visit
     *                    is simply not attributed
     * @param userAgent   used only to tell crawlers from people
     * @return an HTML page carrying the Open Graph tags and a redirect
     */
    @GetMapping(value = "/j/{jobId}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> shareLanding(
            @PathVariable UUID jobId,
            @RequestParam(name = "ch", required = false) String channelCode,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {

        // Same gate as the public Job Board (UC-16): a Paused or Closed job must
        // disappear from a shared link too, not just from the board itself.
        Optional<JobPosition> found = jobPositionRepository.findByIdAndStatus(jobId, JobStatus.PUBLISHED);
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(MediaType.TEXT_HTML)
                    .body(unavailablePage());
        }
        JobPosition job = found.get();

        Optional<PublishingChannel> channel = resolveChannel(channelCode);
        boolean isCrawler = userAgent != null && CRAWLER_USER_AGENT.matcher(userAgent).find();
        if (!isCrawler) {
            channel.ifPresent(value -> jobShareService.recordClick(jobId, value.getCode()));
        }

        String utmSource = channel.map(PublishingChannel::getUtmSource).orElse(null);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(landingPage(job, jobId, utmSource));
    }

    /**
     * An unparseable or unconfigured {@code ch} value is not an error: the
     * link still has to work, the visit simply is not attributed to a channel.
     */
    private Optional<PublishingChannel> resolveChannel(String channelCode) {
        if (channelCode == null || channelCode.isBlank()) {
            return Optional.empty();
        }
        try {
            return publishingChannelRepository.findByCode(PublishingChannelCode.from(channelCode));
        } catch (RuntimeException ex) {
            log.debug("Share link carried an unknown channel code: {}", channelCode);
            return Optional.empty();
        }
    }

    private String landingPage(JobPosition job, UUID jobId, String utmSource) {
        String title = HtmlUtils.htmlEscape(job.getTitle() == null ? "" : job.getTitle());
        String description = HtmlUtils.htmlEscape(shareLinkFactory.ogDescription(job.getDescription()));
        String image = HtmlUtils.htmlEscape(shareLinkFactory.ogImageUrl());
        String canonical = HtmlUtils.htmlEscape(shareLinkFactory.shareUrl(jobId, PublishingChannelCode.COPY_LINK));
        String landing = shareLinkFactory.landingUrl(jobId, utmSource);
        String landingHtml = HtmlUtils.htmlEscape(landing);
        // The redirect target is ours and contains only a UUID and a UTM value that
        // UpdatePublishingChannelRequestDto restricts to [a-z0-9_-], so it cannot
        // carry a quote or angle bracket into the script literal.
        String landingJs = landing.replace("\\", "\\\\").replace("'", "\\'");

        return """
                <!doctype html>
                <html lang="vi">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title>
                <meta property="og:type" content="website">
                <meta property="og:title" content="%s">
                <meta property="og:description" content="%s">
                <meta property="og:image" content="%s">
                <meta property="og:url" content="%s">
                <meta property="og:site_name" content="HireWise">
                <meta name="twitter:card" content="summary_large_image">
                <meta name="twitter:title" content="%s">
                <meta name="twitter:description" content="%s">
                <meta name="twitter:image" content="%s">
                </head>
                <body>
                <p>Đang chuyển tới trang ứng tuyển…</p>
                <noscript><a href="%s">Xem tin tuyển dụng %s</a></noscript>
                <script>location.replace('%s');</script>
                </body>
                </html>
                """.formatted(
                title, title, description, image, canonical,
                title, description, image,
                landingHtml, title, landingJs);
    }

    private String unavailablePage() {
        return """
                <!doctype html>
                <html lang="vi">
                <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>Tin tuyển dụng không còn khả dụng</title>
                <meta name="robots" content="noindex">
                </head>
                <body>
                <h1>Tin tuyển dụng không còn khả dụng</h1>
                <p>Vị trí này đã được đóng hoặc tạm dừng nhận hồ sơ.</p>
                </body>
                </html>
                """;
    }
}
