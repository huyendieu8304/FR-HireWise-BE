package com.hirewise.be.service;

import com.hirewise.be.domain.ConnectionStatus;
import com.hirewise.be.domain.IntegrationConnection;
import com.hirewise.be.domain.IntegrationProvider;
import com.hirewise.be.domain.OauthToken;
import com.hirewise.be.integration.CalendarProviderClient;
import com.hirewise.be.integration.IntegrationConnectException;
import com.hirewise.be.integration.OAuthTokenResponse;
import com.hirewise.be.integration.TokenCipher;
import com.hirewise.be.repository.IntegrationConnectionRepository;
import com.hirewise.be.repository.OauthTokenRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Background worker that silently exchanges the stored {@code refresh_token}
 * for a new {@code access_token} shortly before the current one expires, so
 * the Google Calendar (or Outlook) connection stays {@code CONNECTED} and
 * continues creating Google Meet links automatically (UC-24) WITHOUT the
 * HR Admin having to notice {@code EXPIRED} and click "Ket noi lai".
 *
 * Mirrors CloudStorageTokenRefreshWorker: an untransactional @Scheduled poll
 * that hands each candidate to a @Transactional per-item method, so a failure
 * on one row cannot roll back work already committed for another.
 *
 * Key config properties (all have safe defaults):
 *   app.integration.calendar-token-refresh-poll-interval-ms  -- how often this worker polls (default 5 min).
 *   app.integration.calendar-token-refresh-before-expiry-minutes -- how many minutes before token_expires_at
 *       we attempt the refresh (default 15 min). Must be larger than the poll interval.
 */
@Slf4j
@Component
public class CalendarTokenRefreshWorker {

    private static final String PURPOSE_CALENDAR = "CALENDAR";

    private final Map<IntegrationProvider, CalendarProviderClient> providerClients;
    private final IntegrationConnectionRepository integrationConnectionRepository;
    private final OauthTokenRepository oauthTokenRepository;
    private final AuditLogService auditLogService;
    private final TokenCipher tokenCipher;
    private final Clock clock;
    private final Duration refreshBeforeExpiry;

    public CalendarTokenRefreshWorker(
            List<CalendarProviderClient> providerClients,
            IntegrationConnectionRepository integrationConnectionRepository,
            OauthTokenRepository oauthTokenRepository,
            AuditLogService auditLogService,
            TokenCipher tokenCipher,
            Clock clock,
            @Value("${app.integration.calendar-token-refresh-before-expiry-minutes:15}") long refreshBeforeExpiryMinutes) {
        this.providerClients = providerClients.stream()
                .collect(Collectors.toMap(CalendarProviderClient::provider, Function.identity()));
        this.integrationConnectionRepository = integrationConnectionRepository;
        this.oauthTokenRepository = oauthTokenRepository;
        this.auditLogService = auditLogService;
        this.tokenCipher = tokenCipher;
        this.clock = clock;
        this.refreshBeforeExpiry = Duration.ofMinutes(refreshBeforeExpiryMinutes);
    }

    @Scheduled(fixedDelayString = "${app.integration.calendar-token-refresh-poll-interval-ms:300000}")
    public void refreshExpiringConnection() {
        Optional<IntegrationConnection> maybeConnection =
                integrationConnectionRepository.findFirstByPurposeAndStatusOrderByIdDesc(
                        PURPOSE_CALENDAR, ConnectionStatus.CONNECTED);
        if (maybeConnection.isEmpty()) {
            return;
        }
        IntegrationConnection connection = maybeConnection.get();
        if (connection.getStatus() != ConnectionStatus.CONNECTED) {
            return;
        }
        if (connection.getTokenExpiresAt() == null) {
            return;
        }
        if (connection.getTokenExpiresAt().isAfter(Instant.now(clock).plus(refreshBeforeExpiry))) {
            return;
        }
        log.info("CalendarTokenRefreshWorker: token expiring soon for connectionId={}, attempting refresh",
                connection.getId());
        refreshOne(connection);
    }

    @Transactional
    void refreshOne(IntegrationConnection connection) {
        IntegrationProvider provider = connection.getProvider();
        CalendarProviderClient client = providerClients.get(provider);
        if (client == null) {
            log.error("CalendarTokenRefreshWorker: no CalendarProviderClient for provider {}", provider);
            return;
        }

        Optional<OauthToken> maybeToken =
                oauthTokenRepository.findByIntegrationConnection_Id(connection.getId());
        if (maybeToken.isEmpty() || maybeToken.get().getRefreshTokenEncrypted() == null) {
            log.warn("CalendarTokenRefreshWorker: no refresh_token stored for connectionId={}", connection.getId());
            markExpiredIfActuallyExpired(connection, "no refresh_token stored");
            return;
        }

        OauthToken token = maybeToken.get();
        String refreshToken = tokenCipher.decrypt(token.getRefreshTokenEncrypted());

        OAuthTokenResponse response;
        try {
            response = client.refreshAccessToken(refreshToken);
        } catch (IntegrationConnectException e) {
            log.warn("CalendarTokenRefreshWorker: refresh failed for connectionId={}: {}",
                    connection.getId(), e.getMessage());
            markExpiredIfActuallyExpired(connection, e.getMessage());
            return;
        }

        Instant now = Instant.now(clock);
        Instant newExpiresAt = response.expiresInSeconds() != null
                ? now.plusSeconds(response.expiresInSeconds()) : null;

        token.setAccessTokenEncrypted(tokenCipher.encrypt(response.accessToken()));
        if (response.refreshToken() != null && !response.refreshToken().isBlank()) {
            token.setRefreshTokenEncrypted(tokenCipher.encrypt(response.refreshToken()));
        }
        if (response.tokenType() != null && !response.tokenType().isBlank()) {
            token.setTokenType(response.tokenType());
        }
        token.setExpiresAt(newExpiresAt);
        token.setUpdatedAt(now);
        oauthTokenRepository.save(token);

        connection.setTokenExpiresAt(newExpiresAt);
        connection.setUpdatedAt(now);
        integrationConnectionRepository.save(connection);

        auditLogService.record(null, "CALENDAR_TOKEN_REFRESHED",
                "integration_connections", String.valueOf(connection.getId()));
        log.info("CalendarTokenRefreshWorker: token refreshed for {} (connectionId={}, newExpiresAt={})",
                provider, connection.getId(), newExpiresAt);
    }

    private void markExpiredIfActuallyExpired(IntegrationConnection connection, String reason) {
        Instant now = Instant.now(clock);
        boolean actuallyExpired =
                connection.getTokenExpiresAt() == null || !connection.getTokenExpiresAt().isAfter(now);
        log.warn("CalendarTokenRefreshWorker: refresh failed for {} (connectionId={}, actuallyExpired={}): {}",
                connection.getProvider(), connection.getId(), actuallyExpired, reason);
        if (!actuallyExpired) {
            return;
        }
        connection.setStatus(ConnectionStatus.EXPIRED);
        connection.setUpdatedAt(now);
        integrationConnectionRepository.save(connection);
        auditLogService.record(null, "CALENDAR_TOKEN_REFRESH_FAILED",
                "integration_connections", String.valueOf(connection.getId()));
    }
}
