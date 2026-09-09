package com.hirewise.be.controller;

import com.hirewise.be.domain.PublishingChannelCode;
import com.hirewise.be.dto.request.UpdatePublishingChannelRequestDto;
import com.hirewise.be.dto.response.PublishingChannelResponseDto;
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.security.CurrentUserPrincipal;
import com.hirewise.be.service.PublishingChannelService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * UC-19 - "Cấu hình kênh chia sẻ tin tuyển dụng".
 *
 * <p>Sits under {@code /api/settings} rather than {@code /api/integrations}
 * like UC-07/08/18, because nothing here is an integration any more: there is
 * no OAuth callback, no token and no connection to test. It is a small
 * settings resource in the same family as Pipeline Templates and Email
 * Templates, and reuses their {@code INTEGRATION_MANAGE} permission because
 * {@code V2} already describes that permission as covering the Social API.</p>
 *
 * <p>RBAC: both endpoints require {@code INTEGRATION_MANAGE} (HR Admin only),
 * checked inside the service - there is no per-resource scope or ownership to
 * apply, since channels are global configuration.</p>
 */
@RestController
@RequestMapping("/api/settings/publishing-channels")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class PublishingChannelController {

    PublishingChannelService publishingChannelService;

    /**
     * Lists every sharing channel, including disabled ones.
     *
     * @param currentUser authenticated caller, must have {@code INTEGRATION_MANAGE}
     * @return all channels in display order (never 404 - the four rows are seeded)
     */
    @GetMapping
    public ResponseEntity<List<PublishingChannelResponseDto>> list(
            @CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(publishingChannelService.listChannels(currentUser));
    }

    /**
     * Switches a channel on or off and updates the {@code utm_source} it
     * stamps on share links.
     *
     * @param channelCode which channel to update, e.g. {@code linkedin}
     * @param request     the new enabled flag and UTM source
     * @param currentUser authenticated caller, must have {@code INTEGRATION_MANAGE}
     * @return the updated channel
     */
    @PatchMapping("/{channelCode}")
    public ResponseEntity<PublishingChannelResponseDto> update(
            @PathVariable String channelCode,
            @Valid @RequestBody UpdatePublishingChannelRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser) {
        PublishingChannelCode code = PublishingChannelCode.from(channelCode);
        return ResponseEntity.ok(publishingChannelService.updateChannel(code, request, currentUser));
    }
}
