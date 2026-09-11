package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.PublishingChannel;
import com.hirewise.be.domain.PublishingChannelCode;
import com.hirewise.be.dto.request.UpdatePublishingChannelRequestDto;
import com.hirewise.be.dto.response.PublishingChannelResponseDto;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
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
import java.util.List;

/**
 * UC-19 - "Cấu hình kênh chia sẻ tin tuyển dụng", the HR Admin settings
 * screen behind {@code INTEGRATION_MANAGE}.
 *
 * <p>This use case looks nothing like {@link CalendarIntegrationService} or
 * {@link CloudStorageIntegrationService} even though the SRS originally
 * grouped it with them, and the reason is worth stating: sharing now happens
 * through platform share-intent links, so HireWise never holds a LinkedIn or
 * Facebook credential and there is no OAuth round-trip, no token to encrypt,
 * and no connection that can expire. What is left is plain configuration -
 * which channels are offered, and what {@code utm_source} each one stamps on
 * its links - so there is no {@code integration_connections} row involved
 * anywhere in this class.</p>
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class PublishingChannelService {

    PublishingChannelRepository publishingChannelRepository;
    AccessControlService accessControlService;
    Clock clock;

    /**
     * UC-19 step 1: every channel, enabled or not - the settings screen needs
     * a switch for each one.
     *
     * @param currentUser the authenticated caller (must have {@code INTEGRATION_MANAGE})
     * @return all channels in display order
     */
    @Transactional(readOnly = true)
    public List<PublishingChannelResponseDto> listChannels(CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.INTEGRATION_MANAGE, ResourceContext.none());
        return publishingChannelRepository.findAllByOrderByDisplayOrderAsc().stream()
                .map(PublishingChannelService::toDto)
                .toList();
    }

    /**
     * UC-31: the channels a Recruiter is actually offered in the share modal.
     *
     * <p>No permission check here - this is called from
     * {@code JobShareService}, which is already behind the Job's own
     * {@code @RequiresOwnership} gate. Making it a separate {@code public}
     * method rather than letting that service reach for the repository
     * directly keeps "what counts as an offered channel" in one place.</p>
     *
     * @return the enabled channels, in display order
     */
    @Transactional(readOnly = true)
    public List<PublishingChannel> listEnabledChannels() {
        return publishingChannelRepository.findByEnabledTrueOrderByDisplayOrderAsc();
    }

    /**
     * UC-19 step 2: switches a channel on or off and retunes its
     * {@code utm_source}.
     *
     * <p>Changing {@code utmSource} does not rewrite history: applications
     * that already came in under the previous value keep it, so they stop
     * matching this channel in the UC-32 stats. That is deliberate - rewriting
     * {@code applications.source} would falsify where those candidates
     * genuinely came from.</p>
     *
     * @param code        which channel to update
     * @param request     the new enabled flag and UTM source
     * @param currentUser the authenticated caller (must have {@code INTEGRATION_MANAGE})
     * @return the updated channel
     * @throws ResourceNotFoundException if no channel is configured with this code
     */
    @Transactional
    public PublishingChannelResponseDto updateChannel(
            PublishingChannelCode code, UpdatePublishingChannelRequestDto request, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.INTEGRATION_MANAGE, ResourceContext.none());

        PublishingChannel channel = publishingChannelRepository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.PUBLISHING_CHANNEL_NOT_FOUND, code));

        channel.setEnabled(request.isEnabled());
        channel.setUtmSource(request.getUtmSource());
        channel.setUpdatedAt(Instant.now(clock));
        publishingChannelRepository.save(channel);

        log.info("UC-19: channel {} set to enabled={} utmSource={} by user {}",
                code, request.isEnabled(), request.getUtmSource(), currentUser.userId());
        return toDto(channel);
    }

    private static PublishingChannelResponseDto toDto(PublishingChannel channel) {
        return PublishingChannelResponseDto.builder()
                .code(channel.getCode())
                .name(channel.getName())
                .shareIntentUrlTemplate(channel.getShareIntentUrlTemplate())
                .utmSource(channel.getUtmSource())
                .enabled(channel.isEnabled())
                .displayOrder(channel.getDisplayOrder())
                .build();
    }
}
