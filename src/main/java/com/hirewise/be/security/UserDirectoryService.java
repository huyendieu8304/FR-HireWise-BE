package com.hirewise.be.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hirewise.be.domain.User;
import com.hirewise.be.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * BR-AUTH-07: "a valid JWT alone is not sufficient for access" - every
 * request must verify {@code users.status = ACTIVE} at the time it is
 * processed, not just at the time the token was issued. Hitting the DB on
 * every single request would be too slow, so we use a SHORT-TTL Caffeine
 * cache (45s by default, option B in RBAC Design section 5.3) - fast
 * enough to not slow requests down, short enough that a freshly-blocked
 * account can't keep calling the API for too long. When an HR Admin blocks
 * an account, {@code UserAdminService} evicts the relevant entry
 * immediately instead of waiting out the TTL (combined with
 * {@code KeycloakAdminClient#forceLogout} - option D).
 */
@Component
public class UserDirectoryService {

    private final UserRepository userRepository;
    private final Cache<String, Optional<UserSnapshot>> cache;

    public UserDirectoryService(UserRepository userRepository,
                                 @Value("${app.rbac.user-status-cache-ttl-seconds:45}") long ttlSeconds) {
        this.userRepository = userRepository;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(10_000)
                .build();
    }

    /**
     * Looks up the {@link UserSnapshot} for a Keycloak user id, served from
     * the short-TTL cache when possible.
     *
     * @param keycloakId the "sub" claim of the user's JWT
     * @return the snapshot, or {@link Optional#empty()} if no local user
     *         record exists for this Keycloak id (account not provisioned)
     */
    public Optional<UserSnapshot> lookup(String keycloakId) {
        return cache.get(keycloakId, this::loadFromDb);
    }

    /**
     * Forces the next {@link #lookup} for this user to hit the database
     * instead of a stale cache entry - used right after an admin action
     * that changes {@code users.status} (e.g. blocking an account), so the
     * change takes effect immediately instead of waiting out the TTL.
     *
     * @param keycloakId the "sub" claim identifying the affected user
     */
    public void evict(String keycloakId) {
        cache.invalidate(keycloakId);
    }

    private Optional<UserSnapshot> loadFromDb(String keycloakId) {
        return userRepository.findByKeycloakId(keycloakId).map(this::toSnapshot);
    }

    // Only userId + status are kept - see UserSnapshot for why roleCodes/
    // departmentId were dropped (nothing reads them from here anymore).
    private UserSnapshot toSnapshot(User user) {
        return new UserSnapshot(user.getId(), user.getStatus());
    }
}
