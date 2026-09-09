package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.PublishingChannel;
import com.hirewise.be.domain.PublishingChannelCode;
import com.hirewise.be.dto.request.UpdatePublishingChannelRequestDto;
import com.hirewise.be.dto.response.PublishingChannelResponseDto;
import com.hirewise.be.exception.PermissionDeniedException;
import com.hirewise.be.exception.ResourceNotFoundException;
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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UC-19 - cau hinh kenh chia se tin tuyen dung.
 *
 * <p>Bao phu: man hinh cai dat phai thay ca kenh da tat, modal chia se thi
 * khong; PATCH cap nhat dung 2 truong duoc phep; va quyen INTEGRATION_MANAGE
 * duoc kiem TRUOC khi cham vao DB (neu kiem sau thi user khong quyen van doc
 * duoc du lieu qua thoi gian phan hoi).</p>
 */
@ExtendWith(MockitoExtension.class)
class PublishingChannelServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-09T03:00:00Z");
    private static final Instant EARLIER = Instant.parse("2026-09-01T10:00:00Z");

    @Mock
    private PublishingChannelRepository publishingChannelRepository;
    @Mock
    private AccessControlService accessControlService;

    private PublishingChannelService service;
    private CurrentUser hrAdmin;

    @BeforeEach
    void setUp() {
        service = new PublishingChannelService(
                publishingChannelRepository, accessControlService, Clock.fixed(NOW, ZoneOffset.UTC));
        hrAdmin = new CurrentUser(1L, "admin@hirewise.com", "HR Admin", Set.of("HR_ADMIN"));
    }

    private PublishingChannel channel(PublishingChannelCode code, String utm, boolean enabled) {
        return PublishingChannel.builder()
                .id((long) code.ordinal() + 1)
                .code(code)
                .name(code.name())
                .shareIntentUrlTemplate("https://example.test/share?url={url}")
                .utmSource(utm)
                .enabled(enabled)
                .displayOrder(code.ordinal())
                .createdAt(EARLIER)
                .updatedAt(EARLIER)
                .build();
    }

    @Test
    void listChannelsReturnsDisabledOnesToo() {
        when(publishingChannelRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(List.of(
                channel(PublishingChannelCode.LINKEDIN, "linkedin", true),
                channel(PublishingChannelCode.X, "x", false)));

        List<PublishingChannelResponseDto> channels = service.listChannels(hrAdmin);

        assertThat(channels).hasSize(2);
        assertThat(channels.get(1).isEnabled()).isFalse();
        verify(accessControlService).checkAccess(
                hrAdmin, PermissionCodes.INTEGRATION_MANAGE, ResourceContext.none());
    }

    @Test
    void listEnabledChannelsIsTheShareModalView() {
        when(publishingChannelRepository.findByEnabledTrueOrderByDisplayOrderAsc())
                .thenReturn(List.of(channel(PublishingChannelCode.LINKEDIN, "linkedin", true)));

        assertThat(service.listEnabledChannels()).hasSize(1);
    }

    @Test
    void updateChannelPersistsBothEditableFields() {
        PublishingChannel existing = channel(PublishingChannelCode.FACEBOOK, "facebook", true);
        when(publishingChannelRepository.findByCode(PublishingChannelCode.FACEBOOK))
                .thenReturn(Optional.of(existing));

        PublishingChannelResponseDto updated = service.updateChannel(
                PublishingChannelCode.FACEBOOK,
                new UpdatePublishingChannelRequestDto(false, "fb-campaign"),
                hrAdmin);

        assertThat(updated.isEnabled()).isFalse();
        assertThat(updated.getUtmSource()).isEqualTo("fb-campaign");

        ArgumentCaptor<PublishingChannel> captor = ArgumentCaptor.forClass(PublishingChannel.class);
        verify(publishingChannelRepository).save(captor.capture());
        assertThat(captor.getValue().isEnabled()).isFalse();
        assertThat(captor.getValue().getUtmSource()).isEqualTo("fb-campaign");
        assertThat(captor.getValue().getUpdatedAt()).isEqualTo(NOW);
    }

    /** The share-intent URL is dictated by the platform and must survive an edit untouched. */
    @Test
    void updateChannelLeavesTheShareIntentTemplateAlone() {
        PublishingChannel existing = channel(PublishingChannelCode.LINKEDIN, "linkedin", true);
        when(publishingChannelRepository.findByCode(PublishingChannelCode.LINKEDIN))
                .thenReturn(Optional.of(existing));

        PublishingChannelResponseDto updated = service.updateChannel(
                PublishingChannelCode.LINKEDIN,
                new UpdatePublishingChannelRequestDto(true, "li"),
                hrAdmin);

        assertThat(updated.getShareIntentUrlTemplate()).isEqualTo("https://example.test/share?url={url}");
    }

    @Test
    void updateAnUnconfiguredChannelThrowsResourceNotFound() {
        when(publishingChannelRepository.findByCode(PublishingChannelCode.X)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateChannel(
                PublishingChannelCode.X, new UpdatePublishingChannelRequestDto(true, "x"), hrAdmin))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateWithoutIntegrationManageIsRejectedBeforeAnyDatabaseAccess() {
        CurrentUser recruiter = new CurrentUser(7L, "r@hirewise.com", "R", Set.of("RECRUITER"));
        doThrow(new PermissionDeniedException()).when(accessControlService)
                .checkAccess(recruiter, PermissionCodes.INTEGRATION_MANAGE, ResourceContext.none());

        assertThatThrownBy(() -> service.updateChannel(
                PublishingChannelCode.LINKEDIN, new UpdatePublishingChannelRequestDto(true, "li"), recruiter))
                .isInstanceOf(PermissionDeniedException.class);

        verify(publishingChannelRepository, never()).findByCode(any());
        verify(publishingChannelRepository, never()).save(any());
    }
}
