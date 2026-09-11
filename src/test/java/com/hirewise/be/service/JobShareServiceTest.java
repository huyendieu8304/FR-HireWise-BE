package com.hirewise.be.service;

import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.JobPostingChannel;
import com.hirewise.be.domain.JobStatus;
import com.hirewise.be.domain.PublishingChannel;
import com.hirewise.be.domain.PublishingChannelCode;
import com.hirewise.be.domain.User;
import com.hirewise.be.dto.response.JobShareStatsResponseDto;
import com.hirewise.be.dto.response.JobShareTargetsResponseDto;
import com.hirewise.be.dto.response.ShareTargetResponseDto;
import com.hirewise.be.event.OutboxEventPublisher;
import com.hirewise.be.event.OutboxEventType;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.repository.ApplicationRepository;
import com.hirewise.be.repository.JobPositionRepository;
import com.hirewise.be.repository.JobPostingChannelRepository;
import com.hirewise.be.repository.PublishingChannelRepository;
import com.hirewise.be.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC-31 (chia se ra kenh ngoai) va UC-32 (theo doi hieu qua chia se).
 *
 * <p>Trong tam: BR-POST-01 (chi Job Published moi chia se duoc), BR-POST-02
 * (chia se lai cong don vao dong cu, khong de dong moi), viec loc kenh da bi
 * HR Admin tat (UC-31 EX-01), va cach quy ung vien ve kenh qua utm_source -
 * ke ca truong hop utm_source doi roi khien so lieu cu bi mo coi.</p>
 *
 * <p>ShareLinkFactory duoc dung that (khong mock) vi no chi la ham thuan tren
 * 3 chuoi cau hinh - mock no chi khien test khong con kiem duoc link sinh ra
 * co dung hay khong.</p>
 */
@ExtendWith(MockitoExtension.class)
class JobShareServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T03:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-09-01T10:00:00Z");
    private static final UUID JOB_ID = UUID.randomUUID();

    @Mock
    private JobPositionRepository jobPositionRepository;
    @Mock
    private PublishingChannelRepository publishingChannelRepository;
    @Mock
    private JobPostingChannelRepository jobPostingChannelRepository;
    @Mock
    private ApplicationRepository applicationRepository;
    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    private JobShareService service;
    private CurrentUser recruiter;

    @BeforeEach
    void setUp() {
        ShareLinkFactory linkFactory = new ShareLinkFactory(
                "http://localhost:8080/", "http://localhost:5173", "http://localhost:5173/og-default.png");
        service = new JobShareService(jobPositionRepository, publishingChannelRepository,
                jobPostingChannelRepository, applicationRepository, linkFactory,
                outboxEventPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
        recruiter = new CurrentUser(7L, "recruiter@hirewise.com", "Recruiter One", Set.of("RECRUITER"));
    }

    private JobPosition job(JobStatus status) {
        return job(status, User.builder().id(7L).fullName("Recruiter One")
                .email("recruiter@hirewise.com").build());
    }

    private JobPosition job(JobStatus status, User recruiter) {
        return JobPosition.builder()
                .id(JOB_ID)
                .title("Backend Engineer")
                .description("Xay dung API cho he thong ATS.")
                .openings(2)
                .status(status)
                .recruiter(recruiter)
                .createdAt(EARLIER)
                .updatedAt(EARLIER)
                .build();
    }

    private PublishingChannel channel(PublishingChannelCode code, String template, String utm, boolean enabled) {
        return PublishingChannel.builder()
                .id((long) code.ordinal() + 1)
                .code(code)
                .name(code.name())
                .shareIntentUrlTemplate(template)
                .utmSource(utm)
                .enabled(enabled)
                .displayOrder(code.ordinal())
                .createdAt(EARLIER)
                .updatedAt(EARLIER)
                .build();
    }

    private PublishingChannel linkedIn() {
        return channel(PublishingChannelCode.LINKEDIN,
                "https://www.linkedin.com/sharing/share-offsite/?url={url}", "linkedin", true);
    }

    private JobPostingChannel row(PublishingChannel channel, int shares, int clicks) {
        return JobPostingChannel.builder()
                .id(1L)
                .jobPosition(job(JobStatus.PUBLISHED))
                .channel(channel)
                .shareCount(shares)
                .clickCount(clicks)
                .firstSharedAt(EARLIER)
                .lastSharedAt(EARLIER)
                .createdAt(EARLIER)
                .updatedAt(EARLIER)
                .build();
    }

    private JobPostingChannel savedRow() {
        ArgumentCaptor<JobPostingChannel> captor = ArgumentCaptor.forClass(JobPostingChannel.class);
        verify(jobPostingChannelRepository).save(captor.capture());
        return captor.getValue();
    }

    // ---------- UC-31: listShareTargets ----------

    @Test
    void listShareTargetsBuildsShareLinkAndEncodedIntentUrl() {
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job(JobStatus.PUBLISHED)));
        when(publishingChannelRepository.findByEnabledTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(linkedIn()));
        when(jobPostingChannelRepository.findByJobWithChannel(JOB_ID)).thenReturn(List.of());

        JobShareTargetsResponseDto response = service.listShareTargets(JOB_ID);

        ShareTargetResponseDto target = response.getChannels().getFirst();
        assertThat(target.getShareUrl()).isEqualTo("http://localhost:8080/j/" + JOB_ID + "?ch=LINKEDIN");
        // The share URL must be percent-encoded inside the intent URL, otherwise its own
        // ?ch= parameter would be parsed as a parameter of the LinkedIn page instead.
        assertThat(target.getIntentUrl())
                .startsWith("https://www.linkedin.com/sharing/share-offsite/?url=http%3A%2F%2Flocalhost%3A8080%2Fj%2F")
                .contains("%3Fch%3DLINKEDIN");
        assertThat(target.getShareCount()).isZero();
        assertThat(target.getLastSharedAt()).isNull();
    }

    @Test
    void listShareTargetsLeavesIntentUrlNullForCopyLinkChannel() {
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job(JobStatus.PUBLISHED)));
        when(publishingChannelRepository.findByEnabledTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(channel(PublishingChannelCode.COPY_LINK, null, "direct", true)));
        when(jobPostingChannelRepository.findByJobWithChannel(JOB_ID)).thenReturn(List.of());

        assertThat(service.listShareTargets(JOB_ID).getChannels().getFirst().getIntentUrl()).isNull();
    }

    @Test
    void listShareTargetsCarriesExistingCountersBack() {
        PublishingChannel channel = linkedIn();
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job(JobStatus.PUBLISHED)));
        when(publishingChannelRepository.findByEnabledTrueOrderByDisplayOrderAsc()).thenReturn(List.of(channel));
        when(jobPostingChannelRepository.findByJobWithChannel(JOB_ID)).thenReturn(List.of(row(channel, 3, 11)));

        ShareTargetResponseDto target = service.listShareTargets(JOB_ID).getChannels().getFirst();

        assertThat(target.getShareCount()).isEqualTo(3);
        assertThat(target.getLastSharedAt()).isEqualTo(EARLIER);
    }

    @Test
    void previewCarriesTheJobTitleAndTheConfiguredOgImage() {
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job(JobStatus.PUBLISHED)));
        when(publishingChannelRepository.findByEnabledTrueOrderByDisplayOrderAsc()).thenReturn(List.of());
        when(jobPostingChannelRepository.findByJobWithChannel(JOB_ID)).thenReturn(List.of());

        JobShareTargetsResponseDto response = service.listShareTargets(JOB_ID);

        assertThat(response.getPreview().getTitle()).isEqualTo("Backend Engineer");
        assertThat(response.getPreview().getDescription()).isEqualTo("Xay dung API cho he thong ATS.");
        assertThat(response.getPreview().getImageUrl()).isEqualTo("http://localhost:5173/og-default.png");
        // A trailing slash in the configured base URL must not leak into any link.
        assertThat(response.getPreview().getSiteName()).isEqualTo("http://localhost:8080");
    }

    /** BR-POST-01. */
    @Test
    void listShareTargetsRejectsAJobThatIsNotPublished() {
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job(JobStatus.APPROVED)));

        assertThatThrownBy(() -> service.listShareTargets(JOB_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.JOB_NOT_SHAREABLE);
    }

    @Test
    void listShareTargetsRejectsAPausedJob() {
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job(JobStatus.PAUSED)));

        assertThatThrownBy(() -> service.listShareTargets(JOB_ID))
                .isInstanceOf(BusinessConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.JOB_NOT_SHAREABLE);
    }

    // ---------- UC-31: recordShare ----------

    @Test
    void firstShareCreatesTheRowAndStampsFirstSharedAt() {
        PublishingChannel channel = linkedIn();
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job(JobStatus.PUBLISHED)));
        when(publishingChannelRepository.findByCode(PublishingChannelCode.LINKEDIN)).thenReturn(Optional.of(channel));
        when(jobPostingChannelRepository.findByJobPosition_IdAndChannel_Code(JOB_ID, PublishingChannelCode.LINKEDIN))
                .thenReturn(Optional.empty());

        service.recordShare(JOB_ID, PublishingChannelCode.LINKEDIN, recruiter);

        JobPostingChannel saved = savedRow();
        assertThat(saved.getShareCount()).isEqualTo(1);
        assertThat(saved.getClickCount()).isZero();
        assertThat(saved.getFirstSharedAt()).isEqualTo(NOW);
        assertThat(saved.getLastSharedAt()).isEqualTo(NOW);
        assertThat(saved.getLastSharedByUserId()).isEqualTo(7L);
    }

    /** BR-POST-02: one row per (Job, Channel) - a repeat share updates it in place. */
    @Test
    void repeatShareIncrementsTheExistingRowAndKeepsFirstSharedAt() {
        PublishingChannel channel = linkedIn();
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job(JobStatus.PUBLISHED)));
        when(publishingChannelRepository.findByCode(PublishingChannelCode.LINKEDIN)).thenReturn(Optional.of(channel));
        when(jobPostingChannelRepository.findByJobPosition_IdAndChannel_Code(JOB_ID, PublishingChannelCode.LINKEDIN))
                .thenReturn(Optional.of(row(channel, 2, 5)));

        service.recordShare(JOB_ID, PublishingChannelCode.LINKEDIN, recruiter);

        JobPostingChannel saved = savedRow();
        assertThat(saved.getShareCount()).isEqualTo(3);
        assertThat(saved.getFirstSharedAt()).isEqualTo(EARLIER);
        assertThat(saved.getLastSharedAt()).isEqualTo(NOW);
        // Counting clicks is the landing page's job; sharing again must not touch them.
        assertThat(saved.getClickCount()).isEqualTo(5);
    }

    /** UC-31 EX-01: a stale modal must not slip past HR Admin switching a channel off. */
    @Test
    void shareToADisabledChannelIsRejected() {
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job(JobStatus.PUBLISHED)));
        when(publishingChannelRepository.findByCode(PublishingChannelCode.FACEBOOK))
                .thenReturn(Optional.of(channel(PublishingChannelCode.FACEBOOK, "https://fb/{url}", "facebook", false)));

        assertThatThrownBy(() -> service.recordShare(JOB_ID, PublishingChannelCode.FACEBOOK, recruiter))
                .isInstanceOf(BusinessConflictException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.PUBLISHING_CHANNEL_DISABLED);

        verify(jobPostingChannelRepository, never()).save(any());
    }

    @Test
    void shareToAnUnconfiguredChannelThrowsResourceNotFound() {
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job(JobStatus.PUBLISHED)));
        when(publishingChannelRepository.findByCode(PublishingChannelCode.X)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordShare(JOB_ID, PublishingChannelCode.X, recruiter))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---------- UC-32: recordClick ----------

    @Test
    void recordClickIncrementsOnlyTheClickCounter() {
        PublishingChannel channel = linkedIn();
        when(jobPostingChannelRepository.findByJobPosition_IdAndChannel_Code(JOB_ID, PublishingChannelCode.LINKEDIN))
                .thenReturn(Optional.of(row(channel, 2, 5)));

        service.recordClick(JOB_ID, PublishingChannelCode.LINKEDIN);

        JobPostingChannel saved = savedRow();
        assertThat(saved.getClickCount()).isEqualTo(6);
        assertThat(saved.getShareCount()).isEqualTo(2);
    }

    /** A hand-edited link for a channel this job was never shared to must not 500. */
    @Test
    void recordClickOnAChannelWithNoShareRowIsIgnored() {
        when(jobPostingChannelRepository.findByJobPosition_IdAndChannel_Code(JOB_ID, PublishingChannelCode.X))
                .thenReturn(Optional.empty());

        service.recordClick(JOB_ID, PublishingChannelCode.X);

        verify(jobPostingChannelRepository, never()).save(any());
    }

    // ---------- UC-32: getShareStats ----------

    @Test
    void statsJoinApplicationsToChannelsByUtmSource() {
        PublishingChannel channel = linkedIn();
        when(jobPositionRepository.existsById(JOB_ID)).thenReturn(true);
        when(jobPostingChannelRepository.findByJobWithChannel(JOB_ID)).thenReturn(List.of(row(channel, 4, 12)));
        when(applicationRepository.countByJobGroupedBySource(JOB_ID))
                .thenReturn(List.<Object[]>of(new Object[]{"linkedin", 3L}));
        when(applicationRepository.countByJobPosition_Id(JOB_ID)).thenReturn(10L);

        JobShareStatsResponseDto stats = service.getShareStats(JOB_ID);

        JobShareStatsResponseDto.ChannelStatRow statRow = stats.getRows().getFirst();
        assertThat(statRow.getCode()).isEqualTo(PublishingChannelCode.LINKEDIN);
        assertThat(statRow.getShareCount()).isEqualTo(4);
        assertThat(statRow.getClickCount()).isEqualTo(12);
        assertThat(statRow.getApplicationCount()).isEqualTo(3);
        assertThat(statRow.getShareUrl()).isEqualTo("http://localhost:8080/j/" + JOB_ID + "?ch=LINKEDIN");
        assertThat(stats.getTotalApplications()).isEqualTo(10);
        assertThat(stats.getDirectApplications()).isEqualTo(7);
    }

    /**
     * Changing a channel's utm_source in UC-19 orphans the applications recorded
     * under the old value. That is deliberate - past attribution is never
     * rewritten - so the channel row simply reports zero rather than stealing
     * counts that belong to the previous source.
     */
    @Test
    void applicationsRecordedUnderAPreviousUtmSourceAreNotReattributed() {
        PublishingChannel renamed = channel(PublishingChannelCode.LINKEDIN, "https://li/{url}", "li-new", true);
        when(jobPositionRepository.existsById(JOB_ID)).thenReturn(true);
        when(jobPostingChannelRepository.findByJobWithChannel(JOB_ID)).thenReturn(List.of(row(renamed, 1, 2)));
        when(applicationRepository.countByJobGroupedBySource(JOB_ID))
                .thenReturn(List.<Object[]>of(new Object[]{"linkedin", 3L}));
        when(applicationRepository.countByJobPosition_Id(JOB_ID)).thenReturn(3L);

        JobShareStatsResponseDto stats = service.getShareStats(JOB_ID);

        assertThat(stats.getRows().getFirst().getApplicationCount()).isZero();
        // The 3 orphaned applications must not silently turn into a negative direct count.
        assertThat(stats.getDirectApplications()).isZero();
    }

    @Test
    void statsForAJobNeverSharedReturnNoRowsButStillCountApplications() {
        when(jobPositionRepository.existsById(JOB_ID)).thenReturn(true);
        when(jobPostingChannelRepository.findByJobWithChannel(JOB_ID)).thenReturn(List.of());
        when(applicationRepository.countByJobGroupedBySource(JOB_ID)).thenReturn(List.of());
        when(applicationRepository.countByJobPosition_Id(JOB_ID)).thenReturn(4L);

        JobShareStatsResponseDto stats = service.getShareStats(JOB_ID);

        assertThat(stats.getRows()).isEmpty();
        assertThat(stats.getDirectApplications()).isEqualTo(4);
    }

    // ---------- UC-32 step 4: EM-10 ----------

    @Test
    void notifySummaryQueuesEm10WithOneLinePerChannel() {
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job(JobStatus.PUBLISHED)));
        when(jobPostingChannelRepository.findByJobWithChannel(JOB_ID))
                .thenReturn(List.of(row(linkedIn(), 2, 9)));

        assertThat(service.notifyShareSummary(JOB_ID, recruiter)).isTrue();

        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(outboxEventPublisher).publish(
                eq(OutboxEventType.JOB_SHARE_SUMMARY_EMAIL), payload.capture());
        assertThat(payload.getValue()).containsEntry("email", "recruiter@hirewise.com");
        assertThat(payload.getValue()).containsEntry("jobTitle", "Backend Engineer");
        assertThat((String) payload.getValue().get("channelStatusList"))
                .contains("LINKEDIN").contains("2 lượt chia sẻ").contains("9 lượt xem");
        // EM-10 goes to the Recruiter, so the link must be the internal Job page.
        assertThat(payload.getValue()).containsEntry("jobLink", "http://localhost:5173/jobs/" + JOB_ID);
    }

    @Test
    void notifySummaryDoesNothingWhenTheJobWasNeverShared() {
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job(JobStatus.PUBLISHED)));
        when(jobPostingChannelRepository.findByJobWithChannel(JOB_ID)).thenReturn(List.of());

        assertThat(service.notifyShareSummary(JOB_ID, recruiter)).isFalse();

        verify(outboxEventPublisher, never()).publish(any(), any());
    }

    /** A missing Recruiter email must skip the notification, never fail the request. */
    @Test
    void notifySummaryIsSkippedWhenTheRecruiterHasNoEmail() {
        when(jobPositionRepository.findById(JOB_ID))
                .thenReturn(Optional.of(job(JobStatus.PUBLISHED, User.builder().id(7L).build())));
        when(jobPostingChannelRepository.findByJobWithChannel(JOB_ID))
                .thenReturn(List.of(row(linkedIn(), 1, 0)));

        assertThat(service.notifyShareSummary(JOB_ID, recruiter)).isFalse();

        verify(outboxEventPublisher, never()).publish(any(), any());
    }

    @Test
    void statsForAnUnknownJobThrowResourceNotFound() {
        when(jobPositionRepository.existsById(JOB_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.getShareStats(JOB_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
