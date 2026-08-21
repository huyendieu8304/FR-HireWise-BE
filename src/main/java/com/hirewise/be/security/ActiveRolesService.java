package com.hirewise.be.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hirewise.be.repository.UserRoleRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * resolves the roles currently granted to a user directly from
 * {@code user_roles} on every request (short-TTL cache, same pattern as
 * {@link UserDirectoryService}), instead of baking them into the access
 * token at login time. This is what lets an HR Admin revoke a role
 * (AF-01) and have it take effect within a few seconds, rather than only
 * after the user's token naturally expires or they log in again.
 */
@Component
public class ActiveRolesService {

    private final UserRoleRepository userRoleRepository;
    private final Clock clock;
    private final Cache<Long, Set<String>> cache;

    public ActiveRolesService(UserRoleRepository userRoleRepository,
                               Clock clock,
                               @Value("${app.rbac.user-roles-cache-ttl-seconds:30}") long ttlSeconds) {
        this.userRoleRepository = userRoleRepository;
        this.clock = clock;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(10_000)
                .build();
    }

    public Set<String> rolesOf(Long userId) {
        return cache.get(userId, this::loadFromDb);
    }

    /** Called right after a role is assigned/revoked so the change is visible immediately. */
    public void evict(Long userId) {
        cache.invalidate(userId);
    }

    private Set<String> loadFromDb(Long userId) {
        Instant now = Instant.now(clock);
        return Set.copyOf(userRoleRepository.findActiveRoleCodes(userId, now));
    }
}
