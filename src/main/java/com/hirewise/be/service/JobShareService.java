package com.hirewise.be.service;

import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.JobPostingChannel;
import com.hirewise.be.domain.JobStatus;
import com.hirewise.be.domain.PublishingChannel;
import com.hirewise.be.domain.PublishingChannelCode;
import com.hirewise.be.domain.User;
import com.hirewise.be.dto.response.JobShareStatsResponseDto;
import com.hirewise.be.dto.response.JobShareTargetsResponseDto;
import com.hirewise.be.dto.response.SharePreviewResponseDto;
import com.hirewise.be.dto.response.ShareTargetResponseDto;
import com.hirewise.be.event.OutboxEventPublisher;
import com.hirewise.be.event.OutboxEventType;
import com.hirewise.be.event.OutboxPayloads;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.repository.ApplicationRepository;
import com.hirewise.be.repository.JobPositionRepository;
import com.hirewise.be.repository.JobPostingChannelRepository;
import com.hirewise.be.repository.PublishingChannelRepository;
import com.hirewise.be.security.CurrentUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * UC-31 (chia sẻ tin tuyển dụng ra kênh ngoài) and UC-32 (theo dõi hiệu quả
 * chia sẻ).
 *
 * <p><b>Why there is no worker or queue here.</b> The SRS originally had this
 * use case call the LinkedIn and Facebook posting APIs through a middleware
 * queue, with each channel moving Processing → Success/Failed. Both APIs
 * require an approved developer app, which is not obtainable for this project,
 * so the design was changed to platform share-intent links: HireWise builds a
 * crawlable URL, the Recruiter's browser opens the platform's own share popup,
 * and the platform does the posting. Nothing is asynchronous, so there is no
 * status to track - only counters.</p>
 *
 * <p>Authorization: {@link #listShareTargets} and {@link #recordShare} sit
 * behind {@code @RequiresOwnership} on {@code JobShareController} and do not
 * re-check. {@link #getShareStats} is different - it is a read that Hiring
 * Managers and HR Admin also need, so the controller gates it on plain
 * {@code JOB_VIEW} instead and this class is not involved either way.</p>
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class JobShareService {

    JobPositionRepository jobPositionRepository;
    PublishingChannelRepository publishingChannelRepository;
    JobPostingChannelRepository jobPostingChannelRepository;
    ApplicationRepository applicationRepository;
    ShareLinkFactory shareLinkFactory;
    OutboxEventPublisher outboxEventPublisher;
    Clock clock;

    /**
     * UC-31 steps 1-2: the Open Graph card preview plus every enabled channel,
     * each with its share link and platform popup URL already built.
     *
     * @param jobId id of the Published job to share
     * @return the preview and the channels on offer
     * @throws ResourceNotFoundException if no job exists with {@code jobId}
     * @throws BusinessConflictException BR-POST-01: the job is not {@code PUBLISHED}
     */
    @Transactional(readOnly = true)
    public JobShareTargetsResponseDto listShareTargets(UUID jobId) {
        JobPosition job = loadShareableJob(jobId);

        List<PublishingChannel> enabled = publishingChannelRepository.findByEnabledTrueOrderByDisplayOrderAsc();
        Map<PublishingChannelCode, JobPostingChannel> existing = existingRowsByCode(jobId);

        List<ShareTargetResponseDto> targets = new ArrayList<>(enabled.size());
        for (PublishingChannel channel : enabled) {
            String shareUrl = shareLinkFactory.shareUrl(jobId, channel.getCode());
            JobPostingChannel row = existing.get(channel.getCode());
            targets.add(ShareTargetResponseDto.builder()
                    .code(channel.getCode())
                    .name(channel.getName())
                    .shareUrl(shareUrl)
                    .intentUrl(shareLinkFactory.intentUrl(channel, shareUrl))
                    .shareCount(row != null ? row.getShareCount() : 0)
                    .lastSharedAt(row != null ? row.getLastSharedAt() : null)
                    .build());
        }

        return JobShareTargetsResponseDto.builder()
                .preview(previewOf(job))
                .channels(targets)
                .build();
    }

    /**
     * UC-31 step 3: records that the Recruiter pressed [Chia sẻ] for this
     * channel.
     *
     * <p>BR-POST-02 in its revised form: a repeat share increments the
     * existing row rather than inserting a second one, which the
     * {@code uk_job_posting_channels_job_channel} unique constraint also
     * enforces at the database level.</p>
     *
     * <p>This is fire-and-forget by nature. The share popup belongs to
     * LinkedIn or Facebook, so we genuinely cannot know whether the Recruiter
     * completed the post or closed the window - {@code share_count} means
     * "times the button was pressed", and the honest signal for reach is
     * {@code click_count}, which only a real visit can produce.</p>
     *
     * @param jobId       id of the Published job being shared
     * @param code        the channel being shared to
     * @param currentUser the Recruiter performing the share
     * @throws ResourceNotFoundException  if the job or the channel does not exist
     * @throws BusinessConflictException  if the job is not Published, or the channel is disabled
     */
    @Transactional
    public void recordShare(UUID jobId, PublishingChannelCode code, CurrentUser currentUser) {
        JobPosition job = loadShareableJob(jobId);
        PublishingChannel channel = publishingChannelRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PUBLISHING_CHANNEL_NOT_FOUND, code));
        // UC-31 EX-01: the frontend hides disabled channels, but a stale modal
        // or a direct API call must not slip past HR Admin's decision.
        if (!channel.isEnabled()) {
            throw new BusinessConflictException(ErrorCode.PUBLISHING_CHANNEL_DISABLED, code);
        }

        Instant now = Instant.now(clock);
        JobPostingChannel row = jobPostingChannelRepository
                .findByJobPosition_IdAndChannel_Code(jobId, code)
                .orElseGet(() -> newRow(job, channel, now));

        row.setShareCount(row.getShareCount() + 1);
        row.setLastSharedAt(now);
        row.setLastSharedByUserId(currentUser.userId());
        row.setUpdatedAt(now);
        jobPostingChannelRepository.save(row);

        log.info("UC-31: job {} shared to {} by user {} (shareCount={})",
                jobId, code, currentUser.userId(), row.getShareCount());
    }

    /**
     * UC-32 step 3 / the Open Graph landing page: counts one real visit
     * arriving through a share link.
     *
     * <p>Called from {@code PublicJobShareController}, which has already
     * filtered out crawler user agents. Silently does nothing when the pair
     * has no row yet - that means someone hand-edited a link for a channel
     * this job was never shared to, which is not worth an error page on a
     * public URL.</p>
     *
     * @param jobId id of the job whose link was opened
     * @param code  the channel named in the {@code ch} query parameter
     */
    @Transactional
    public void recordClick(UUID jobId, PublishingChannelCode code) {
        Optional<JobPostingChannel> found =
                jobPostingChannelRepository.findByJobPosition_IdAndChannel_Code(jobId, code);
        if (found.isEmpty()) {
            log.debug("UC-32: click on job {} via {} has no share row - not counted", jobId, code);
            return;
        }
        JobPostingChannel row = found.get();
        row.setClickCount(row.getClickCount() + 1);
        row.setUpdatedAt(Instant.now(clock));
        jobPostingChannelRepository.save(row);
    }

    /**
     * UC-32 steps 1-2: shares, clicks and attributed applications per channel.
     *
     * <p>Applications are joined to channels by {@code utm_source} rather than
     * by a foreign key, because the value is captured from the candidate's URL
     * at apply time - long after, and independently of, whichever
     * {@code job_posting_channels} row produced that link.</p>
     *
     * @param jobId id of the job to report on
     * @return one row per channel already shared to, plus the totals
     * @throws ResourceNotFoundException if no job exists with {@code jobId}
     */
    @Transactional(readOnly = true)
    public JobShareStatsResponseDto getShareStats(UUID jobId) {
        if (!jobPositionRepository.existsById(jobId)) {
            throw new ResourceNotFoundException(ErrorCode.JOB_POSITION_NOT_FOUND, jobId);
        }

        Map<String, Long> applicationsBySource = new HashMap<>();
        for (Object[] pair : applicationRepository.countByJobGroupedBySource(jobId)) {
            applicationsBySource.put((String) pair[0], (Long) pair[1]);
        }

        List<JobShareStatsResponseDto.ChannelStatRow> rows =
                jobPostingChannelRepository.findByJobWithChannel(jobId).stream()
                        .map(row -> toStatRow(jobId, row, applicationsBySource))
                        .toList();

        long total = applicationRepository.countByJobPosition_Id(jobId);
        long attributed = applicationsBySource.values().stream().mapToLong(Long::longValue).sum();

        return JobShareStatsResponseDto.builder()
                .rows(rows)
                .totalApplications(total)
                // Anything without a utm_source came straight to the Job Board. Derived by
                // subtraction rather than a third query, and clamped at zero so a source
                // value that no longer matches any channel can never make this negative.
                .directApplications(Math.max(0, total - attributed))
                .build();
    }

    private JobShareStatsResponseDto.ChannelStatRow toStatRow(
            UUID jobId, JobPostingChannel row, Map<String, Long> applicationsBySource) {
        PublishingChannel channel = row.getChannel();
        return JobShareStatsResponseDto.ChannelStatRow.builder()
                .code(channel.getCode())
                .name(channel.getName())
                .shareCount(row.getShareCount())
                .clickCount(row.getClickCount())
                .applicationCount(applicationsBySource.getOrDefault(channel.getUtmSource(), 0L))
                .lastSharedAt(row.getLastSharedAt())
                .shareUrl(shareLinkFactory.shareUrl(jobId, channel.getCode()))
                .build();
    }

    /**
     * UC-32 step 4 (EM-10): emails the Recruiter a summary of which channels
     * this Job has been shared to.
     *
     * <p>Triggered when the Recruiter closes the share modal rather than by a
     * worker finishing, because with share-intent links there is no background
     * job to finish - the sharing is already done by the time the modal
     * closes.</p>
     *
     * <p>Does nothing when the Job has never been shared, or when the Job has
     * no Recruiter with an email on file: an EM-10 listing no channels would
     * be noise, and silently skipping is better than failing the request over
     * a notification.</p>
     *
     * @param jobId       id of the shared job
     * @param currentUser the Recruiter who did the sharing
     * @return {@code true} if a summary email was queued
     */
    @Transactional
    public boolean notifyShareSummary(UUID jobId, CurrentUser currentUser) {
        JobPosition job = loadShareableJob(jobId);
        List<JobPostingChannel> rows = jobPostingChannelRepository.findByJobWithChannel(jobId);
        if (rows.isEmpty()) {
            return false;
        }

        User recruiter = job.getRecruiter();
        String toEmail = recruiter != null ? recruiter.getEmail() : null;
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("UC-32: job {} has no recruiter email - EM-10 not sent", jobId);
            return false;
        }

        outboxEventPublisher.publish(
                OutboxEventType.JOB_SHARE_SUMMARY_EMAIL,
                OutboxPayloads.jobShareSummaryEmail(
                        toEmail,
                        recruiter.getFullName(),
                        job.getTitle(),
                        renderChannelList(rows),
                        shareLinkFactory.jobDetailUrl(jobId)));
        log.info("UC-32: EM-10 queued for job {} to {}", jobId, toEmail);
        return true;
    }

    /**
     * EM-10 has a single {@code {{Channel_Status_List}}} placeholder and
     * {@code EmailServiceImpl} only does flat string substitution, so the
     * per-channel lines have to be flattened into one string here.
     */
    private static String renderChannelList(List<JobPostingChannel> rows) {
        StringBuilder list = new StringBuilder();
        for (JobPostingChannel row : rows) {
            list.append("- ").append(row.getChannel().getName())
                    .append(": ").append(row.getShareCount()).append(" lượt chia sẻ, ")
                    .append(row.getClickCount()).append(" lượt xem<br>");
        }
        return list.toString();
    }

    /** BR-POST-01: only a Published Job may be shared to an external channel. */
    private JobPosition loadShareableJob(UUID jobId) {
        JobPosition job = jobPositionRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.JOB_POSITION_NOT_FOUND, jobId));
        if (job.getStatus() != JobStatus.PUBLISHED) {
            throw new BusinessConflictException(ErrorCode.JOB_NOT_SHAREABLE, job.getStatus());
        }
        return job;
    }

    private Map<PublishingChannelCode, JobPostingChannel> existingRowsByCode(UUID jobId) {
        Map<PublishingChannelCode, JobPostingChannel> byCode = new HashMap<>();
        for (JobPostingChannel row : jobPostingChannelRepository.findByJobWithChannel(jobId)) {
            byCode.put(row.getChannel().getCode(), row);
        }
        return byCode;
    }

    private static JobPostingChannel newRow(JobPosition job, PublishingChannel channel, Instant now) {
        return JobPostingChannel.builder()
                .jobPosition(job)
                .channel(channel)
                .shareCount(0)
                .clickCount(0)
                .firstSharedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private SharePreviewResponseDto previewOf(JobPosition job) {
        return SharePreviewResponseDto.builder()
                .title(job.getTitle())
                .description(shareLinkFactory.ogDescription(job.getDescription()))
                .imageUrl(shareLinkFactory.ogImageUrl())
                .siteName(shareLinkFactory.publicBaseUrl())
                .build();
    }
}
