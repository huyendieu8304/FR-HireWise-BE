package com.hirewise.be.controller;

import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.JobStatus;
import com.hirewise.be.domain.PublishingChannel;
import com.hirewise.be.domain.PublishingChannelCode;
import com.hirewise.be.repository.JobPositionRepository;
import com.hirewise.be.repository.PublishingChannelRepository;
import com.hirewise.be.service.JobShareService;
import com.hirewise.be.service.ShareLinkFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Trang landing Open Graph cua link chia se (UC-31/UC-32).
 *
 * <p>Viet o muc unit test (goi thang method, khong bootstrap Spring) thay vi
 * {@code @WebMvcTest}: du an chua co slice test nao, va toan bo logic dang ke
 * cua controller nay - nhan dien crawler, dung the og:, escape HTML, chan Job
 * khong con Published - deu nam trong chinh method, khong phu thuoc gi vao
 * tang MVC.</p>
 *
 * <p>{@link ShareLinkFactory} dung ban that vi day chinh la thu can kiem: URL
 * trong the {@code og:url} phai khop chinh xac link da chia se ra ngoai.</p>
 */
@ExtendWith(MockitoExtension.class)
class PublicJobShareControllerTest {

    private static final UUID JOB_ID = UUID.randomUUID();
    private static final Instant EARLIER = Instant.parse("2026-09-01T10:00:00Z");
    private static final String HUMAN_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/140.0 Safari/537.36";
    private static final String FACEBOOK_UA = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)";
    private static final String LINKEDIN_UA = "LinkedInBot/1.0 (compatible; Mozilla/5.0; Apache-HttpClient +http://www.linkedin.com)";

    @Mock
    private JobPositionRepository jobPositionRepository;
    @Mock
    private PublishingChannelRepository publishingChannelRepository;
    @Mock
    private JobShareService jobShareService;

    private PublicJobShareController controller;

    @BeforeEach
    void setUp() {
        ShareLinkFactory linkFactory = new ShareLinkFactory(
                "http://localhost:8080", "http://localhost:5173", "http://localhost:5173/og-default.png");
        controller = new PublicJobShareController(
                jobPositionRepository, publishingChannelRepository, jobShareService, linkFactory);
    }

    private JobPosition job(String title, String description) {
        return JobPosition.builder()
                .id(JOB_ID)
                .title(title)
                .description(description)
                .openings(1)
                .status(JobStatus.PUBLISHED)
                .createdAt(EARLIER)
                .updatedAt(EARLIER)
                .build();
    }

    private void givenPublishedJob(String title, String description) {
        when(jobPositionRepository.findByIdAndStatus(JOB_ID, JobStatus.PUBLISHED))
                .thenReturn(Optional.of(job(title, description)));
    }

    private void givenLinkedInChannel() {
        when(publishingChannelRepository.findByCode(PublishingChannelCode.LINKEDIN))
                .thenReturn(Optional.of(PublishingChannel.builder()
                        .id(1L)
                        .code(PublishingChannelCode.LINKEDIN)
                        .name("LinkedIn")
                        .shareIntentUrlTemplate("https://www.linkedin.com/sharing/share-offsite/?url={url}")
                        .utmSource("linkedin")
                        .enabled(true)
                        .displayOrder(1)
                        .createdAt(EARLIER)
                        .updatedAt(EARLIER)
                        .build()));
    }

    @Test
    void pageCarriesEveryOpenGraphTagACrawlerNeeds() {
        givenPublishedJob("Backend Engineer", "Xay dung API cho he thong ATS.");
        givenLinkedInChannel();

        String html = controller.shareLanding(JOB_ID, "LINKEDIN", FACEBOOK_UA).getBody();

        assertThat(html)
                .contains("<meta property=\"og:title\" content=\"Backend Engineer\">")
                .contains("<meta property=\"og:description\" content=\"Xay dung API cho he thong ATS.\">")
                .contains("<meta property=\"og:image\" content=\"http://localhost:5173/og-default.png\">")
                .contains("<meta property=\"og:url\" content=\"http://localhost:8080/j/" + JOB_ID)
                .contains("<meta property=\"og:type\" content=\"website\">")
                .contains("<meta name=\"twitter:card\" content=\"summary_large_image\">");
    }

    /**
     * Facebook and LinkedIn crawl a link the moment it is pasted. Counting that
     * fetch would mean every single share instantly registered a phantom visitor.
     */
    @Test
    void facebookCrawlerDoesNotCountAsAClick() {
        givenPublishedJob("Backend Engineer", "JD");
        givenLinkedInChannel();

        controller.shareLanding(JOB_ID, "LINKEDIN", FACEBOOK_UA);

        verify(jobShareService, never()).recordClick(any(), any());
    }

    @Test
    void linkedInCrawlerDoesNotCountAsAClick() {
        givenPublishedJob("Backend Engineer", "JD");
        givenLinkedInChannel();

        controller.shareLanding(JOB_ID, "LINKEDIN", LINKEDIN_UA);

        verify(jobShareService, never()).recordClick(any(), any());
    }

    @Test
    void aRealBrowserCountsAsAClickOnTheNamedChannel() {
        givenPublishedJob("Backend Engineer", "JD");
        givenLinkedInChannel();

        controller.shareLanding(JOB_ID, "LINKEDIN", HUMAN_UA);

        verify(jobShareService).recordClick(JOB_ID, PublishingChannelCode.LINKEDIN);
    }

    @Test
    void aVisitWithNoChannelParameterIsServedButNotAttributed() {
        givenPublishedJob("Backend Engineer", "JD");

        ResponseEntity<String> response = controller.shareLanding(JOB_ID, null, HUMAN_UA);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(jobShareService, never()).recordClick(any(), any());
        // With no channel there is no utm_source, so the redirect must stay clean.
        assertThat(response.getBody()).contains("/careers/" + JOB_ID + "/apply');");
    }

    /** A hand-mangled link must still open the job, just without attribution. */
    @Test
    void anUnknownChannelParameterDoesNotBreakThePage() {
        givenPublishedJob("Backend Engineer", "JD");

        ResponseEntity<String> response = controller.shareLanding(JOB_ID, "myspace", HUMAN_UA);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(jobShareService, never()).recordClick(any(), any());
    }

    @Test
    void aRealVisitorIsRedirectedToTheApplyPageWithUtmParameters() {
        givenPublishedJob("Backend Engineer", "JD");
        givenLinkedInChannel();

        String html = controller.shareLanding(JOB_ID, "LINKEDIN", HUMAN_UA).getBody();

        assertThat(html).contains("location.replace('http://localhost:5173/careers/" + JOB_ID
                + "/apply?utm_source=linkedin&utm_medium=social&utm_campaign=job_" + JOB_ID + "');");
        // Bots and no-JS browsers need a way through too.
        assertThat(html).contains("<noscript><a href=\"http://localhost:5173/careers/" + JOB_ID);
    }

    /**
     * Job titles are free text typed by a Recruiter and land straight inside an
     * HTML attribute, so an unescaped quote would break out of the og:title tag.
     */
    @Test
    void specialCharactersInTheTitleAreEscaped() {
        givenPublishedJob("Senior \"Backend\" <Engineer> & Co", "JD");

        String html = controller.shareLanding(JOB_ID, null, HUMAN_UA).getBody();

        assertThat(html).contains(
                "<meta property=\"og:title\" content=\"Senior &quot;Backend&quot; &lt;Engineer&gt; &amp; Co\">");
        assertThat(html).doesNotContain("<Engineer>");
    }

    @Test
    void aMultiLineDescriptionIsFlattenedIntoOneAttribute() {
        givenPublishedJob("Backend Engineer", "Dong mot\nDong hai\t\tDong ba");

        String html = controller.shareLanding(JOB_ID, null, HUMAN_UA).getBody();

        assertThat(html).contains("<meta property=\"og:description\" content=\"Dong mot Dong hai Dong ba\">");
    }

    /** UC-44: pausing or closing a Job has to kill its already-shared links too. */
    @Test
    void aJobThatIsNoLongerPublishedReturns404Html() {
        when(jobPositionRepository.findByIdAndStatus(JOB_ID, JobStatus.PUBLISHED)).thenReturn(Optional.empty());

        ResponseEntity<String> response = controller.shareLanding(JOB_ID, "LINKEDIN", HUMAN_UA);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("Tin tuyển dụng không còn khả dụng");
        verify(jobShareService, never()).recordClick(any(), any());
    }

    @Test
    void aRequestWithNoUserAgentHeaderIsTreatedAsAPerson() {
        givenPublishedJob("Backend Engineer", "JD");
        givenLinkedInChannel();

        controller.shareLanding(JOB_ID, "LINKEDIN", null);

        verify(jobShareService).recordClick(JOB_ID, PublishingChannelCode.LINKEDIN);
    }
}
