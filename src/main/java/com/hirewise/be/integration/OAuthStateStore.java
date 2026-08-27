package com.hirewise.be.integration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hirewise.be.domain.IntegrationProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Short-lived, single-use anti-CSRF store for the OAuth 2.0 {@code state}
 * parameter (UC-07/UC-08). The HR Admin's own access token cannot be
 * carried through the provider's redirect (the callback is a plain browser
 * navigation with no Authorization header - see
 * {@code CloudStorageIntegrationController}), so {@code state} is what
 * proves the callback corresponds to a connect attempt this backend
 * actually initiated, and lets the callback recover which user started it.
 * <p>
 * Same pattern as {@code authorization.RolePermissionCache} /
 * {@code security.UserDirectoryService}: an in-memory Caffeine cache is
 * fine here because a "pending connect" only needs to survive a single HR
 * Admin's OAuth round trip (well under the 10-minute TTL below), not a
 * server restart.
 */
@Component
public class OAuthStateStore {

    private static final Duration TTL = Duration.ofMinutes(10);

    /** Who started a pending Cloud Storage connect/reconnect attempt. */
    public record PendingConnection(IntegrationProvider provider, Long userId) {
    }

    private final Cache<String, PendingConnection> cache = Caffeine.newBuilder()
            .expireAfterWrite(TTL)
            .maximumSize(1_000)
            .build();

    /**
     * Issues a new one-time state value for a connect attempt about to start.
     *
     * @param provider the provider the HR Admin is connecting to
     * @param userId   id of the HR Admin initiating the connect/reconnect
     * @return the opaque state value to embed in the authorization URL
     */
    public String issue(IntegrationProvider provider, Long userId) {
        String state = UUID.randomUUID().toString();
        cache.put(state, new PendingConnection(provider, userId));
        return state;
    }

    /**
     * Validates and consumes a state value from a callback request. Single-use:
     * a state value is removed as soon as it is looked up, whether or not the
     * rest of the callback goes on to succeed.
     *
     * @param state the {@code state} query parameter from the provider's redirect
     * @return the matching pending connection, or {@code null} if {@code state} is
     *         missing, unknown, expired, or already consumed
     */
    public PendingConnection consume(String state) {
        if (state == null) {
            return null;
        }
        PendingConnection pending = cache.getIfPresent(state);
        if (pending != null) {
            cache.invalidate(state);
        }
        return pending;
    }
}
