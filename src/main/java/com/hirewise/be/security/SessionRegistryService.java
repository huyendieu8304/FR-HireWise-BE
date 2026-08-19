package com.hirewise.be.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hirewise.be.domain.UserSession;
import com.hirewise.be.repository.UserSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * still cryptographically-valid access token must
 * stop working the instant its {@code user_session} row is revoked - this
 * is the session/token-registry half of RBAC layer 1 (Authentication
 * Freshness), complementing {@link UserDirectoryService}'s account-status
 * check. Short-TTL cache so logout has near-immediate effect ({@link #evict}
 * is also called synchronously right when the session is revoked).
 */
@Component
public class SessionRegistryService {

    private final UserSessionRepository sessionRepository;
    private final Clock clock;
    private final Cache<UUID, Boolean> activeCache;

    public SessionRegistryService(UserSessionRepository sessionRepository,
                                   Clock clock,
                                   @Value("${app.rbac.session-cache-ttl-seconds:20}") long ttlSeconds) {
        this.sessionRepository = sessionRepository;
        this.clock = clock;
        this.activeCache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(50_000)
                .build();
    }

    /** @return {@code true} if the session exists, is not revoked and has not expired. */
    public boolean isActive(UUID sessionId) {
        if (sessionId == null) {
            return false;
        }
        return activeCache.get(sessionId, this::loadFromDb);
    }

    /** Called on logout so the revocation is visible on the very next request. */
    public void evict(UUID sessionId) {
        activeCache.invalidate(sessionId);
    }

    private boolean loadFromDb(UUID sessionId) {
        Optional<UserSession> session = sessionRepository.findById(sessionId);
        if (session.isEmpty()) {
            return false;
        }
        UserSession s = session.get();
        Instant now = Instant.now(clock);
        return s.getRevokedAt() == null && s.getExpiresAt().isAfter(now);
    }
}
