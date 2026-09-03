package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.ConnectionStatus;
import com.hirewise.be.domain.IntegrationConnection;
import com.hirewise.be.domain.IntegrationProvider;
import com.hirewise.be.domain.OauthToken;
import com.hirewise.be.dto.response.CalendarConnectionStatusResponseDto;
import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.integration.CalendarProviderClient;
import com.hirewise.be.integration.GoogleCalendarProviderClient;
import com.hirewise.be.integration.IntegrationConnectException;
import com.hirewise.be.integration.OAuthStateStore;
import com.hirewise.be.integration.OAuthTokenResponse;
import com.hirewise.be.integration.TokenCipher;
import com.hirewise.be.repository.IntegrationConnectionRepository;
import com.hirewise.be.repository.OauthTokenRepository;
import com.hirewise.be.repository.UserRepository;
import com.hirewise.be.security.CurrentUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * UC-18 — Configure and sync Calendar API.
 *
 * <p>Reuses the same OAuth infrastructure as UC-07/UC-08 (Cloud Storage):
 * {@code integration_connections} with {@code purpose=CALENDAR},
 * encrypted {@code oauth_tokens}, and the {@link OAuthStateStore} for
 * the anti-CSRF state parameter. The company-level MVP assumption from
 * UC-07 applies here too: only one active Calendar connection at a time.</p>
 *
 * <p>Normal Flow (UC-18 steps 1-6):</p>
 * <ol>
 *   <li>{@link #buildAuthorizationUrl} builds the OAuth consent URL.</li>
 *   <li>HR Admin authorizes in the browser popup.</li>
 *   <li>{@link #handleCallback} exchanges the code and persists the
 *       connection + encrypted tokens.</li>
 *   <li>{@link #testConnection} probes the Calendar API to confirm the
 *       connection is live (BR-INTEG-01).</li>
 *   <li>{@link #disconnect} revokes the connection (soft delete).</li>
 * </ol>
 */
@Slf4j
@Service
public class CalendarIntegrationService {

    private static final String PURPOSE_CALENDAR = "CALENDAR";

    private final Map<IntegrationProvider, CalendarProviderClient> providerClients;
    private final IntegrationConnectionRepository integrationConnectionRepository;
    private final OauthTokenRepository oauthTokenRepository;
    private final UserRepository userRepository;
    private final AccessControlService accessControlService;
    private final AuditLogService auditLogService;
    private final OAuthStateStore oauthStateStore;
    private final TokenCipher tokenCipher;
    private final Clock clock;

    public CalendarIntegrationService(
            List<CalendarProviderClient> providerClients,
            IntegrationConnectionRepository integrationConnectionRepository,
            OauthTokenRepository oauthTokenRepository,
            UserRepository userRepository,
            AccessControlService accessControlService,
            AuditLogService auditLogService,
            OAuthStateStore oauthStateStore,
            TokenCipher tokenCipher,
            Clock clock) {
        this.providerClients = providerClients.stream()
                .collect(Collectors.toMap(CalendarProviderClient::provider, Function.identity()));
        this.integrationConnectionRepository = integrationConnectionRepository;
        this.oauthTokenRepository = oauthTokenRepository;
        this.userRepository = userRepository;
        this.accessControlService = accessControlService;
        this.auditLogService = auditLogService;
        this.oauthStateStore = oauthStateStore;
        this.tokenCipher = tokenCipher;
        this.clock = clock;
    }

    /**
     * UC-18 — Current Calendar connection status.
     *
     * @param currentUser the authenticated caller (must have {@code INTEGRATION_MANAGE})
     * @return current status, or {@link CalendarConnectionStatusResponseDto#notConnected()}
     *         if Calendar has never been connected
     */
    public CalendarConnectionStatusResponseDto getStatus(CurrentUser currentUser) {
        accessControlService.checkAccess(
                currentUser, PermissionCodes.INTEGRATION_MANAGE, ResourceContext.none());
        return integrationConnectionRepository.findFirstByPurposeAndStatusOrderByIdDesc(PURPOSE_CALENDAR, ConnectionStatus.CONNECTED)
                .map(this::toStatusDto)
                .orElseGet(CalendarConnectionStatusResponseDto::notConnected);
    }

    /**
     * UC-24: Creates a Google Calendar event with Google Meet link if Google Calendar is connected.
     * Automatically refreshes the access token if it has expired.
     */
    public java.util.Optional<String> createGoogleMeetMeeting(
            String summary,
            String description,
            java.time.LocalDateTime startDateTime,
            java.time.LocalDateTime endDateTime) {

        // 1. Find the active GOOGLE_CALENDAR connection
        IntegrationConnection conn = integrationConnectionRepository
                .findFirstByProviderAndPurposeOrderByIdDesc(IntegrationProvider.GOOGLE_CALENDAR, PURPOSE_CALENDAR)
                .filter(c -> c.getStatus() == ConnectionStatus.CONNECTED)
                .orElse(null);
        if (conn == null) {
            log.info("createGoogleMeetMeeting: Google Calendar not connected — skipping");
            return java.util.Optional.empty();
        }

        // 2. Load stored token
        OauthToken token = oauthTokenRepository.findByIntegrationConnection_Id(conn.getId()).orElse(null);
        if (token == null) {
            log.warn("createGoogleMeetMeeting: No OAuth token found for connectionId={}", conn.getId());
            return java.util.Optional.empty();
        }

        // 3. Refresh token if expired (or within 60s of expiry)
        String accessToken = tokenCipher.decrypt(token.getAccessTokenEncrypted());
        if (token.getExpiresAt() != null
                && Instant.now(clock).isAfter(token.getExpiresAt().minusSeconds(60))) {
            log.info("createGoogleMeetMeeting: Access token expired — refreshing");
            String refreshToken = token.getRefreshTokenEncrypted() != null
                    ? tokenCipher.decrypt(token.getRefreshTokenEncrypted()) : null;
            if (refreshToken == null || refreshToken.isBlank()) {
                log.warn("createGoogleMeetMeeting: No refresh token available — cannot refresh");
                return java.util.Optional.empty();
            }
            try {
                OAuthTokenResponse refreshed = clientFor(IntegrationProvider.GOOGLE_CALENDAR)
                        .refreshAccessToken(refreshToken);
                accessToken = refreshed.accessToken();
                // Persist the refreshed access token
                token.setAccessTokenEncrypted(tokenCipher.encrypt(accessToken));
                if (refreshed.expiresInSeconds() != null) {
                    Instant newExpiry = Instant.now(clock).plusSeconds(refreshed.expiresInSeconds());
                    token.setExpiresAt(newExpiry);
                    conn.setTokenExpiresAt(newExpiry);
                    conn.setUpdatedAt(Instant.now(clock));
                    integrationConnectionRepository.save(conn);
                }
                token.setUpdatedAt(Instant.now(clock));
                oauthTokenRepository.save(token);
                log.info("createGoogleMeetMeeting: Token refreshed successfully");
            } catch (Exception e) {
                log.warn("createGoogleMeetMeeting: Token refresh failed: {}", e.getMessage());
                return java.util.Optional.empty();
            }
        }

        // 4. Create Google Calendar event with Meet conference
        try {
            CalendarProviderClient client = clientFor(IntegrationProvider.GOOGLE_CALENDAR);
            if (client instanceof GoogleCalendarProviderClient googleClient) {
                String meetLink = googleClient.createMeetingEvent(
                        accessToken, summary, description, startDateTime, endDateTime);
                if (meetLink != null && !meetLink.isBlank()) {
                    log.info("createGoogleMeetMeeting: Created Meet link: {}", meetLink);
                    return java.util.Optional.of(meetLink);
                }
            }
        } catch (Exception e) {
            log.warn("createGoogleMeetMeeting: Failed to create meeting event: {}", e.getMessage());
        }
        return java.util.Optional.empty();
    }


    /**
     * UC-18 step 2-3: builds the OAuth consent URL for the given provider.
     *
     * @param providerPathSegment path variable value, e.g. {@code "google-calendar"}
     * @param currentUser         the authenticated caller (must have {@code INTEGRATION_MANAGE})
     * @return the full OAuth 2.0 authorization URL
     * @throws BadRequestException if the provider path segment is unknown
     */
    public String buildAuthorizationUrl(String providerPathSegment, CurrentUser currentUser) {
        accessControlService.checkAccess(
                currentUser, PermissionCodes.INTEGRATION_MANAGE, ResourceContext.none());
        IntegrationProvider provider = resolveProvider(providerPathSegment);
        String state = oauthStateStore.issue(provider, currentUser.userId());
        log.info("Calendar {} connect initiated by userId={}", provider, currentUser.userId());
        return clientFor(provider).buildAuthorizationUrl(state);
    }

    /**
     * UC-18 step 5-7: handles the provider's redirect back to us after consent.
     * Deliberately never throws to the caller — the redirect comes from the
     * provider's browser navigation, not an API client that can read JSON errors.
     * Every failure path is reported as {@code false}.
     *
     * @param providerPathSegment path variable value, e.g. {@code "google-calendar"}
     * @param code                {@code code} query param, present on success
     * @param state               anti-CSRF value that must match a pending connect
     * @param error               present when the HR Admin denied consent
     * @return {@code true} if the connection was established, {@code false} otherwise
     */
    @Transactional
    public boolean handleCallback(
            String providerPathSegment, String code, String state, String error) {
        IntegrationProvider provider;
        try {
            provider = resolveProvider(providerPathSegment);
        } catch (Exception e) {
            log.warn("Calendar callback for unknown provider path '{}'", providerPathSegment);
            return false;
        }

        OAuthStateStore.PendingConnection pending = oauthStateStore.consume(state);
        if (pending == null || pending.provider() != provider) {
            log.warn("Calendar {} callback rejected: missing, expired, or mismatched state",
                    provider);
            return false;
        }

        if (error != null || code == null || code.isBlank()) {
            // EX-01: HR Admin denied consent, or the provider errored out.
            log.warn("Calendar {} connect did not complete (error={})", provider, error);
            return false;
        }

        OAuthTokenResponse tokenResponse;
        try {
            tokenResponse = clientFor(provider).exchangeAuthorizationCode(code);
        } catch (IntegrationConnectException e) {
            log.warn("Calendar {} token exchange failed: {}", provider, e.getMessage());
            return false;
        }

        persistConnection(provider, pending.userId(), tokenResponse);
        return true;
    }

    /**
     * UC-18 step 4 — Test Connection (BR-INTEG-01): probes the Calendar API
     * with the currently stored access token to confirm the connection is live.
     *
     * @param currentUser the authenticated caller (must have {@code INTEGRATION_MANAGE})
     * @return the current status after the probe (always {@code CONNECTED} on success)
     * @throws BusinessConflictException   if Calendar has never been connected
     * @throws BusinessConflictException   if the connection is not in {@code CONNECTED} status
     * @throws BadRequestException         if the provider rejects the access token
     */
    @Transactional
    public CalendarConnectionStatusResponseDto testConnection(CurrentUser currentUser) {
        accessControlService.checkAccess(
                currentUser, PermissionCodes.INTEGRATION_MANAGE, ResourceContext.none());

        IntegrationConnection connection = integrationConnectionRepository
                .findFirstByPurposeAndStatusOrderByIdDesc(PURPOSE_CALENDAR, ConnectionStatus.CONNECTED)
                .orElseThrow(() -> new BusinessConflictException(ErrorCode.CALENDAR_NOT_CONNECTED));

        if (connection.getStatus() != ConnectionStatus.CONNECTED) {
            // Cannot test a revoked/expired connection — ask HR Admin to reconnect first.
            throw new BusinessConflictException(ErrorCode.CALENDAR_NOT_CONNECTED);
        }

        OauthToken token = oauthTokenRepository
                .findByIntegrationConnection_Id(connection.getId())
                .orElseThrow(() -> new BusinessConflictException(ErrorCode.CALENDAR_NOT_CONNECTED));

        String accessToken = tokenCipher.decrypt(token.getAccessTokenEncrypted());
        try {
            clientFor(connection.getProvider()).testConnection(accessToken);
        } catch (IntegrationConnectException e) {
            throw new BadRequestException(
                    ErrorCode.CALENDAR_TEST_CONNECTION_FAILED, e.getMessage());
        }

        log.info("Calendar test connection passed for userId={} (connectionId={})",
                currentUser.userId(), connection.getId());
        return toStatusDto(connection);
    }

    /**
     * UC-18 AF-01 (Disconnect): revokes the current connection. Soft state
     * change only — row is kept for audit trail; a later Connect reuses it.
     *
     * @param currentUser the authenticated caller (must have {@code INTEGRATION_MANAGE})
     * @return the updated (now Revoked) status
     * @throws BusinessConflictException if Calendar has never been connected
     */
    @Transactional
    public CalendarConnectionStatusResponseDto disconnect(CurrentUser currentUser) {
        accessControlService.checkAccess(
                currentUser, PermissionCodes.INTEGRATION_MANAGE, ResourceContext.none());

        IntegrationConnection connection = integrationConnectionRepository
                .findFirstByPurposeAndStatusOrderByIdDesc(PURPOSE_CALENDAR, ConnectionStatus.CONNECTED)
                .orElseThrow(() -> new BusinessConflictException(ErrorCode.CALENDAR_NOT_CONNECTED));

        connection.setStatus(ConnectionStatus.REVOKED);
        connection.setUpdatedAt(Instant.now(clock));
        integrationConnectionRepository.save(connection);

        auditLogService.record(currentUser.userId(), "CALENDAR_DISCONNECTED",
                "integration_connections", String.valueOf(connection.getId()));
        log.info("Calendar disconnected by userId={} (connectionId={})",
                currentUser.userId(), connection.getId());

        return getStatus(currentUser);
    }

    /**
     * Upserts the {@code integration_connections} and {@code oauth_tokens}
     * rows for a successful Calendar token exchange. Reused by both a
     * first-time Connect and a Reconnect — the only difference is whether
     * a row already existed to update.
     */
    private void persistConnection(
            IntegrationProvider provider, Long actorUserId, OAuthTokenResponse tokenResponse) {
        Instant now = Instant.now(clock);

        IntegrationConnection connection = integrationConnectionRepository
                .findFirstByProviderAndPurposeOrderByIdDesc(provider, PURPOSE_CALENDAR)
                .orElseGet(() -> IntegrationConnection.builder()
                        .provider(provider)
                        .purpose(PURPOSE_CALENDAR)
                        .createdAt(now)
                        .build());
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setCreatedBy(userRepository.getReferenceById(actorUserId));
        connection.setConnectedAt(now);
        connection.setTokenExpiresAt(tokenResponse.expiresInSeconds() != null
                ? now.plusSeconds(tokenResponse.expiresInSeconds()) : null);
        connection.setUpdatedAt(now);

        IntegrationConnection saved = integrationConnectionRepository.save(connection);

        OauthToken token = oauthTokenRepository
                .findByIntegrationConnection_Id(saved.getId())
                .orElseGet(() -> OauthToken.builder()
                        .integrationConnection(saved)
                        .createdAt(now)
                        .build());
        token.setAccessTokenEncrypted(tokenCipher.encrypt(tokenResponse.accessToken()));
        // Keep existing refresh token when provider doesn't reissue one
        // (Google only reissues on prompt=consent; Microsoft may reissue any time).
        if (tokenResponse.refreshToken() != null && !tokenResponse.refreshToken().isBlank()) {
            token.setRefreshTokenEncrypted(tokenCipher.encrypt(tokenResponse.refreshToken()));
        }
        token.setTokenType(tokenResponse.tokenType() != null ? tokenResponse.tokenType() : "Bearer");
        token.setExpiresAt(connection.getTokenExpiresAt());
        token.setUpdatedAt(now);
        oauthTokenRepository.save(token);

        auditLogService.record(actorUserId, "CALENDAR_CONNECTED",
                "integration_connections", String.valueOf(saved.getId()));
        log.info("Calendar {} connected by userId={} (connectionId={})",
                provider, actorUserId, saved.getId());
    }

    private IntegrationProvider resolveProvider(String pathSegment) {
        // Only accept Calendar-purpose providers — block accidental use of
        // cloud-storage path segments on this endpoint.
        IntegrationProvider provider = IntegrationProvider.fromPathSegment(pathSegment);
        if (provider != IntegrationProvider.GOOGLE_CALENDAR
                && provider != IntegrationProvider.OUTLOOK_CALENDAR) {
            throw new BadRequestException(
                    ErrorCode.CALENDAR_PROVIDER_UNSUPPORTED, pathSegment);
        }
        return provider;
    }

    private CalendarProviderClient clientFor(IntegrationProvider provider) {
        CalendarProviderClient client = providerClients.get(provider);
        if (client == null) {
            // Programming error — every CalendarProvider enum value must have
            // a matching @Component implementing CalendarProviderClient.
            throw new IllegalStateException(
                    "No CalendarProviderClient registered for provider " + provider);
        }
        return client;
    }

    private CalendarConnectionStatusResponseDto toStatusDto(IntegrationConnection connection) {
        return CalendarConnectionStatusResponseDto.builder()
                .connected(true)
                .provider(connection.getProvider())
                .status(connection.getStatus())
                .connectedAt(connection.getConnectedAt())
                .tokenExpiresAt(connection.getTokenExpiresAt())
                .build();
    }
}
