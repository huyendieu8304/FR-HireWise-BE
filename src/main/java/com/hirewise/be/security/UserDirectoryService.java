package com.hirewise.be.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hirewise.be.domain.UserStatus;
import com.hirewise.be.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * BR-AUTH-07: "a valid access token alone is not sufficient for access" -
 * every request must verify {@code users.status = ACTIVE} at the time it is
 * processed, not just at the time the token was issued. Hitting the DB on
 * every single request would be too slow, so we use a SHORT-TTL Caffeine
 * cache (45s by default) - fast enough to not slow requests down, short
 * enough that a freshly-blocked account can't keep calling the API for too
 * long. When an HR Admin blocks an account, {@code UserAdminService} evicts
 * the relevant entry immediately instead of waiting out the TTL (combined
 * with revoking every {@code user_sessions} row - see
 * {@code UserSessionRepository#revokeAllActiveForUser}).
 */
@Component
public class UserDirectoryService {

    private final UserRepository userRepository;
    private final Cache<Long, Optional<UserSnapshot>> cache;

    public UserDirectoryService(UserRepository userRepository,
                                 @Value("${app.rbac.user-status-cache-ttl-seconds:45}") long ttlSeconds) {
        this.userRepository = userRepository;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(10_000)
                .build();
    }

    /**
     * Looks up the {@link UserSnapshot} for an internal user id, served
     * from the short-TTL cache when possible.
     *
     * @param userId the "sub" claim of the caller's access token
     * @return the snapshot, or {@link Optional#empty()} if no local user
     *         record exists for this id
     */
    public Optional<UserSnapshot> lookup(Long userId) {
        return cache.get(userId, this::loadFromDb);
    }

    /**
     * Forces the next {@link #lookup} for this user to hit the database
     * instead of a stale cache entry - used right after an admin action
     * that changes {@code users.status} (e.g. blocking an account), so the
     * change takes effect immediately instead of waiting out the TTL.
     *
     * @param userId internal id of the affected user
     */
    public void evict(Long userId) {
        cache.invalidate(userId);
    }

    private Optional<UserSnapshot> loadFromDb(Long userId) {
        return userRepository.findStatusById(userId)
                .map(status -> new UserSnapshot(userId, status));
    }
}
