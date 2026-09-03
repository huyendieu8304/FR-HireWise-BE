package com.hirewise.be.service;

import com.hirewise.be.domain.ConnectionStatus;
import com.hirewise.be.domain.IntegrationConnection;
import com.hirewise.be.domain.IntegrationProvider;
import com.hirewise.be.domain.OauthToken;
import com.hirewise.be.integration.CloudStorageProviderClient;
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
 * Background worker silently exchanges a stored {@code refresh_token} for a fresh
 * {@code access_token} shortly before the current one expires, so a
 * {@code CONNECTED} Cloud Storage connection stays usable across restarts
 * and normal token lifetimes WITHOUT the HR Admin having to notice
 * {@code EXPIRED} and click "Kết nối lại" (which reruns the full OAuth 2.0
 * consent screen - see {@code CloudStorageIntegrationService}).
 * <p>
 * Mirrors {@code event.OutboxDispatcher}'s shape: a {@code @Scheduled} poll
 * method (untransactional) that hands each candidate off to a
 * {@code @Transactional} per-item method, so a failure on one row can never
 * roll back work already committed for another.
 * <p>
 * Scoped to only the CURRENT Cloud Storage connection (same
 * {@code findFirstByPurposeOrderByIdDesc(PURPOSE_CLOUD_STORAGE)} lookup
 * {@code CloudStorageIntegrationService.disconnect()} uses) rather than
 * every {@code CONNECTED} row ever created - the MVP assumes a single
 * shared company connection at a time (see that service's class javadoc),
 * so an older row left behind by switching provider is not "the"
 * connection anyone is using and does not need to be kept alive.
 */
@Slf4j
@Component
public class CloudStorageTokenRefreshWorker {

    private static final String PURPOSE_CLOUD_STORAGE = "CLOUD_STORAGE";

    private final Map<IntegrationProvider, CloudStorageProviderClient> providerClients;
    private final IntegrationConnectionRepository integrationConnectionRepository;
    private final OauthTokenRepository oauthTokenRepository;
    private final AuditLogService auditLogService;
    private final TokenCipher tokenCipher;
    private final Clock clock;
    private final Duration refreshBeforeExpiry;

    public CloudStorageTokenRefreshWorker(
            List<CloudStorageProviderClient> providerClients,
            IntegrationConnectionRepository integrationConnectionRepository,
            OauthTokenRepository oauthTokenRepository,
            AuditLogService auditLogService,
            TokenCipher tokenCipher,
            Clock clock,
            @Value("${app.integration.token-refresh-before-expiry-minutes:15}") long refreshBeforeExpiryMinutes) {
        this.providerClients = providerClients.stream()
                .collect(Collectors.toMap(CloudStorageProviderClient::provider, Function.identity()));
        this.integrationConnectionRepository = integrationConnectionRepository;
        this.oauthTokenRepository = oauthTokenRepository;
        this.auditLogService = auditLogService;
        this.tokenCipher = tokenCipher;
        this.clock = clock;
        this.refreshBeforeExpiry = Duration.ofMinutes(refreshBeforeExpiryMinutes);
    }

    @Scheduled(fixedDelayString = "${app.integration.token-refresh-poll-interval-ms:300000}")
    public void refreshExpiringConnection() {
        Optional<IntegrationConnection> maybeConnection =
                integrationConnectionRepository.findFirstByPurposeAndStatusOrderByIdDesc(PURPOSE_CLOUD_STORAGE, ConnectionStatus.CONNECTED);
        if (maybeConnection.isEmpty()) {
            return; // never connected - nothing to do
        }

        IntegrationConnection connection = maybeConnection.get();
        if (connection.getStatus() != ConnectionStatus.CONNECTED) {
            return; // already EXPIRED (waiting on a manual Reconnect) or REVOKED - not our job
        }
        if (connection.getTokenExpiresAt() == null) {
            return; // provider never returned expires_in on connect - nothing to pre-empt
        }
        if (connection.getTokenExpiresAt().isAfter(Instant.now(clock).plus(refreshBeforeExpiry))) {
            return; // not close to expiring yet
        }

        refreshOne(connection);
    }

    @Transactional
    void refreshOne(IntegrationConnection connection) {
        IntegrationProvider provider = connection.getProvider();
        CloudStorageProviderClient client = providerClients.get(provider);
        if (client == null) {
            // Programming error, not a user-facing one - see the identical check in
            // CloudStorageIntegrationService#clientFor.
            log.error("No CloudStorageProviderClient registered for provider {}", provider);
            return;
        }

        Optional<OauthToken> maybeToken = oauthTokenRepository.findByIntegrationConnection_Id(connection.getId());
        if (maybeToken.isEmpty() || maybeToken.get().getRefreshTokenEncrypted() == null) {
            // Nothing to refresh WITH (e.g. Google only issued a refresh_token on the
            // very first-ever consent and this row somehow never got one) - only
            // option left is the normal manual Reconnect flow.
            log.warn("Cloud storage {} token refresh skipped: no refresh_token stored (connectionId={})",
                    provider, connection.getId());
            markExpiredIfActuallyExpired(connection, "no refresh_token stored");
            return;
        }
        OauthToken token = maybeToken.get();
        String refreshToken = tokenCipher.decrypt(token.getRefreshTokenEncrypted());

        OAuthTokenResponse response;
        try {
            response = client.refreshAccessToken(refreshToken);
        } catch (IntegrationConnectException e) {
            markExpiredIfActuallyExpired(connection, e.getMessage());
            return;
        }

        Instant now = Instant.now(clock);
        Instant newExpiresAt = response.expiresInSeconds() != null
                ? now.plusSeconds(response.expiresInSeconds()) : null;

        token.setAccessTokenEncrypted(tokenCipher.encrypt(response.accessToken()));
        // Most providers (both implemented here included) do not reissue a refresh_token
        // on every refresh call - only overwrite the stored one if a new one came back.
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

        auditLogService.record(null, "CLOUD_STORAGE_TOKEN_REFRESHED",
                "integration_connections", String.valueOf(connection.getId()));
        log.info("Cloud storage {} token refreshed automatically (connectionId={}, newExpiresAt={})",
                provider, connection.getId(), newExpiresAt);
    }

    /**
     * A single failed refresh attempt is not necessarily fatal - it could be a
     * transient network blip, and {@link #refreshExpiringConnection} already
     * fires several minutes before the CURRENT access token actually expires
     * (see {@code app.integration.token-refresh-before-expiry-minutes}), so
     * there are normally a few more poll cycles left to retry successfully
     * before anything actually breaks. Only flip the connection to
     * {@code EXPIRED} (which pauses uploads per BR-STORAGE-02 and requires a
     * manual Reconnect, UC-08 AF-01) once the stored {@code tokenExpiresAt} has
     * actually passed - i.e. retries are exhausted and the access token is
     * genuinely unusable now.
     */
    private void markExpiredIfActuallyExpired(IntegrationConnection connection, String reason) {
        Instant now = Instant.now(clock);
        boolean actuallyExpired =
                connection.getTokenExpiresAt() == null || !connection.getTokenExpiresAt().isAfter(now);
        log.warn("Cloud storage {} token refresh failed (connectionId={}, actuallyExpired={}): {}",
                connection.getProvider(), connection.getId(), actuallyExpired, reason);
        if (!actuallyExpired) {
            return;
        }
        connection.setStatus(ConnectionStatus.EXPIRED);
        connection.setUpdatedAt(now);
        integrationConnectionRepository.save(connection);
        auditLogService.record(null, "CLOUD_STORAGE_TOKEN_REFRESH_FAILED",
                "integration_connections", String.valueOf(connection.getId()));
    }
}
