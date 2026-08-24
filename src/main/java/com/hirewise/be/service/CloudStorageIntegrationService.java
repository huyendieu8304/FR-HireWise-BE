package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.ConnectionStatus;
import com.hirewise.be.domain.IntegrationConnection;
import com.hirewise.be.domain.IntegrationProvider;
import com.hirewise.be.domain.OauthToken;
import com.hirewise.be.domain.StorageConnection;
import com.hirewise.be.dto.response.StorageConnectionStatusResponseDto;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.integration.CloudStorageProviderClient;
import com.hirewise.be.integration.IntegrationConnectException;
import com.hirewise.be.integration.OAuthStateStore;
import com.hirewise.be.integration.OAuthTokenResponse;
import com.hirewise.be.integration.TokenCipher;
import com.hirewise.be.repository.IntegrationConnectionRepository;
import com.hirewise.be.repository.OauthTokenRepository;
import com.hirewise.be.repository.StorageConnectionRepository;
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
 * UC-07 (Connect Cloud Storage via OAuth 2.0) and UC-08 (check/reconnect/
 * disconnect). The MVP assumes a single shared company connection at a time
 * (see UC-07 "Assumptions"), so every lookup here is "the current one for
 * purpose=CLOUD_STORAGE", not scoped to the calling user.
 * <p>
 * {@link #buildAuthorizationUrl} and {@link #handleCallback} together
 * implement Connect (UC-07) AND Reconnect (UC-08 AF-01, which is
 * "reruns UC-07 steps 3-6" per the SRS) - both are the exact same OAuth
 * round trip; {@code handleCallback} just updates the existing connection
 * row in place instead of always inserting a new one.
 */
@Slf4j
@Service
public class CloudStorageIntegrationService {

    private static final String PURPOSE_CLOUD_STORAGE = "CLOUD_STORAGE";

    private final Map<IntegrationProvider, CloudStorageProviderClient> providerClients;
    private final IntegrationConnectionRepository integrationConnectionRepository;
    private final OauthTokenRepository oauthTokenRepository;
    private final StorageConnectionRepository storageConnectionRepository;
    private final UserRepository userRepository;
    private final AccessControlService accessControlService;
    private final AuditLogService auditLogService;
    private final OAuthStateStore oauthStateStore;
    private final TokenCipher tokenCipher;
    private final Clock clock;

    public CloudStorageIntegrationService(List<CloudStorageProviderClient> providerClients,
                                           IntegrationConnectionRepository integrationConnectionRepository,
                                           OauthTokenRepository oauthTokenRepository,
                                           StorageConnectionRepository storageConnectionRepository,
                                           UserRepository userRepository,
                                           AccessControlService accessControlService,
                                           AuditLogService auditLogService,
                                           OAuthStateStore oauthStateStore,
                                           TokenCipher tokenCipher,
                                           Clock clock) {
        this.providerClients = providerClients.stream()
                .collect(Collectors.toMap(CloudStorageProviderClient::provider, Function.identity()));
        this.integrationConnectionRepository = integrationConnectionRepository;
        this.oauthTokenRepository = oauthTokenRepository;
        this.storageConnectionRepository = storageConnectionRepository;
        this.userRepository = userRepository;
        this.accessControlService = accessControlService;
        this.auditLogService = auditLogService;
        this.oauthStateStore = oauthStateStore;
        this.tokenCipher = tokenCipher;
        this.clock = clock;
    }

    /**
     * UC-08 step 2: current Cloud Storage connection status.
     *
     * @param currentUser the authenticated caller (must have {@code INTEGRATION_MANAGE})
     * @return the current status, or {@link StorageConnectionStatusResponseDto#notConnected()}
     *         if Cloud Storage has never been connected (UC-07 has never completed)
     */
    public StorageConnectionStatusResponseDto getStatus(CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.INTEGRATION_MANAGE, ResourceContext.none());
        return storageConnectionRepository.findFirstByOrderByIdDesc()
                .map(this::toStatusDto)
                .orElseGet(StorageConnectionStatusResponseDto::notConnected);
    }

    /**
     * UC-07 step 1-3 / UC-08 AF-01 step 3-4 (Reconnect): builds the URL to
     * send the HR Admin's browser to for the provider's consent screen.
     *
     * @param providerPathSegment path variable value, e.g. {@code "google-drive"}
     * @param currentUser         the authenticated caller (must have {@code INTEGRATION_MANAGE})
     * @return the full OAuth 2.0 authorization URL
     */
    public String buildAuthorizationUrl(String providerPathSegment, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.INTEGRATION_MANAGE, ResourceContext.none());
        IntegrationProvider provider = IntegrationProvider.fromPathSegment(providerPathSegment);
        String state = oauthStateStore.issue(provider, currentUser.userId());
        log.info("Cloud storage {} connect initiated by userId={}", provider, currentUser.userId());
        return clientFor(provider).buildAuthorizationUrl(state);
    }

    /**
     * UC-07 step 5-7 / UC-08 AF-01 step 4 (Reconnect): handles the
     * provider's redirect back to us. Deliberately never throws to the
     * caller (see {@code controller.CloudStorageIntegrationController}) -
     * the caller here is the provider's own redirect, not an API client
     * that could read a JSON error body, so every failure path (EX-01:
     * user denied consent, state missing/expired, token exchange failed)
     * is reported back as a plain {@code false}.
     *
     * @param providerPathSegment path variable value, e.g. {@code "google-drive"}
     * @param code                the {@code code} query parameter, present on success
     * @param state               the {@code state} query parameter that must match a
     *                            pending connection issued by {@link #buildAuthorizationUrl}
     * @param error               the {@code error} query parameter the provider sets when
     *                            the HR Admin denied consent (e.g. {@code "access_denied"})
     * @return {@code true} if the connection was established, {@code false} otherwise
     */
    @Transactional
    public boolean handleCallback(String providerPathSegment, String code, String state, String error) {
        IntegrationProvider provider;
        try {
            provider = IntegrationProvider.fromPathSegment(providerPathSegment);
        } catch (Exception e) {
            log.warn("Cloud storage callback for unknown provider path '{}'", providerPathSegment);
            return false;
        }

        OAuthStateStore.PendingConnection pending = oauthStateStore.consume(state);
        if (pending == null || pending.provider() != provider) {
            log.warn("Cloud storage {} callback rejected: missing, expired, or mismatched state", provider);
            return false;
        }

        if (error != null || code == null || code.isBlank()) {
            // EX-01: the HR Admin denied consent, or the provider itself errored out.
            log.warn("Cloud storage {} connect did not complete (error={})", provider, error);
            return false;
        }

        OAuthTokenResponse tokenResponse;
        try {
            tokenResponse = clientFor(provider).exchangeAuthorizationCode(code);
        } catch (IntegrationConnectException e) {
            log.warn("Cloud storage {} token exchange failed: {}", provider, e.getMessage());
            return false;
        }

        persistConnection(provider, pending.userId(), tokenResponse);
        return true;
    }

    /**
     * UC-08 AF-01 (Disconnect): revokes the current connection. Soft state
     * change only (BR-STORAGE-02 spirit) - the row is kept, not deleted, so
     * its history/audit trail survives; a later Connect (UC-07) reuses the
     * same {@code integration_connections}/{@code storage_connections} rows.
     *
     * @param currentUser the authenticated caller (must have {@code INTEGRATION_MANAGE})
     * @return the updated status
     * @throws BusinessConflictException if Cloud Storage was never connected
     */
    @Transactional
    public StorageConnectionStatusResponseDto disconnect(CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.INTEGRATION_MANAGE, ResourceContext.none());

        IntegrationConnection connection = integrationConnectionRepository
                .findFirstByPurposeOrderByIdDesc(PURPOSE_CLOUD_STORAGE)
                .orElseThrow(() -> new BusinessConflictException(ErrorCode.INTEGRATION_NOT_CONNECTED));

        connection.setStatus(ConnectionStatus.REVOKED);
        connection.setUpdatedAt(Instant.now(clock));
        integrationConnectionRepository.save(connection);

        auditLogService.record(currentUser.userId(), "CLOUD_STORAGE_DISCONNECTED",
                "integration_connections", String.valueOf(connection.getId()));
        log.info("Cloud storage disconnected by userId={} (connectionId={})", currentUser.userId(), connection.getId());

        return getStatus(currentUser);
    }

    /**
     * Upserts the {@code integration_connections}/{@code oauth_tokens}/
     * {@code storage_connections} rows for a successful token exchange, and
     * best-effort creates the BR-STORAGE-03 root folder. Reused as-is by
     * both a first-time Connect (UC-07) and a Reconnect (UC-08 AF-01) - the
     * only difference is whether a row already existed to update.
     */
    private void persistConnection(IntegrationProvider provider, Long actorUserId, OAuthTokenResponse tokenResponse) {
        Instant now = Instant.now(clock);

        IntegrationConnection connection = integrationConnectionRepository
                .findFirstByProviderAndPurposeOrderByIdDesc(provider, PURPOSE_CLOUD_STORAGE)
                .orElseGet(() -> IntegrationConnection.builder()
                        .provider(provider)
                        .purpose(PURPOSE_CLOUD_STORAGE)
                        .createdAt(now)
                        .build());
        connection.setStatus(ConnectionStatus.CONNECTED);
        connection.setCreatedBy(userRepository.getReferenceById(actorUserId));
        connection.setConnectedAt(now);
        connection.setTokenExpiresAt(tokenResponse.expiresInSeconds() != null
                ? now.plusSeconds(tokenResponse.expiresInSeconds()) : null);
        connection.setUpdatedAt(now);

        IntegrationConnection savedConnection = integrationConnectionRepository.save(connection);

        OauthToken token = oauthTokenRepository.findByIntegrationConnection_Id(savedConnection.getId())
                .orElseGet(() -> OauthToken.builder()
                        .integrationConnection(savedConnection) // Reference savedConnection here
                        .createdAt(now)
                        .build());

        token.setAccessTokenEncrypted(tokenCipher.encrypt(tokenResponse.accessToken()));
        // Some reconnects legitimately don't return a fresh refresh_token (e.g. Google
        // only reissues one when prompt=consent forces re-approval) - keep the previous
        // one in that case rather than overwriting it with null.
        if (tokenResponse.refreshToken() != null && !tokenResponse.refreshToken().isBlank()) {
            token.setRefreshTokenEncrypted(tokenCipher.encrypt(tokenResponse.refreshToken()));
        }
        token.setTokenType(tokenResponse.tokenType() != null ? tokenResponse.tokenType() : "Bearer");
        token.setExpiresAt(connection.getTokenExpiresAt());
        token.setUpdatedAt(now);
        oauthTokenRepository.save(token);

        StorageConnection storageConnection = storageConnectionRepository.findByIntegrationConnection_Id(connection.getId())
                .orElseGet(() -> StorageConnection.builder()
                        .integrationConnection(connection)
                        .provider(provider)
                        .createdAt(now)
                        .build());
        if (storageConnection.getRootFolderId() == null) {
            // Best-effort (see CloudStorageProviderClient#createRootFolder) - a failure here
            // does not fail the connection itself, only leaves rootFolderId null for now.
            storageConnection.setRootFolderId(clientFor(provider).createRootFolder(tokenResponse.accessToken()));
        }
        storageConnection.setUpdatedAt(now);
        storageConnection = storageConnectionRepository.save(storageConnection);

        auditLogService.record(actorUserId, "CLOUD_STORAGE_CONNECTED",
                "storage_connections", String.valueOf(storageConnection.getId()));
        log.info("Cloud storage {} connected by userId={} (connectionId={})", provider, actorUserId, connection.getId());
    }

    private CloudStorageProviderClient clientFor(IntegrationProvider provider) {
        CloudStorageProviderClient client = providerClients.get(provider);
        if (client == null) {
            // Programming error, not a user-facing one: every IntegrationProvider value
            // must have a matching @Component implementing CloudStorageProviderClient.
            throw new IllegalStateException("No CloudStorageProviderClient registered for provider " + provider);
        }
        return client;
    }

    private StorageConnectionStatusResponseDto toStatusDto(StorageConnection storageConnection) {
        IntegrationConnection connection = storageConnection.getIntegrationConnection();
        return StorageConnectionStatusResponseDto.builder()
                .connected(true)
                .provider(storageConnection.getProvider())
                .status(connection.getStatus())
                .accountLabel(connection.getAccountLabel())
                .rootFolderId(storageConnection.getRootFolderId())
                .connectedAt(connection.getConnectedAt())
                .tokenExpiresAt(connection.getTokenExpiresAt())
                .build();
    }
}
