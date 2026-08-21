package com.hirewise.be.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Deliberately a simple in-process fixed-window counter (Caffeine, same
 * building block used elsewhere in this codebase) rather than a dedicated
 * rate-limiting library - good enough for a single-instance deployment;
 * a multi-instance deployment would need a shared store (e.g. Redis)
 * instead.
 */
@Component
public class LoginRateLimiter {

    private final Cache<String, AtomicInteger> attemptsByIp;
    private final int maxAttemptsPerWindow;

    public LoginRateLimiter(@Value("${app.auth.ip-rate-limit-max-attempts:20}") int maxAttemptsPerWindow,
                             @Value("${app.auth.ip-rate-limit-window-minutes:15}") long windowMinutes) {
        this.maxAttemptsPerWindow = maxAttemptsPerWindow;
        this.attemptsByIp = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(windowMinutes))
                .maximumSize(100_000)
                .build();
    }

    /**
     * Records one login attempt from {@code ipAddress} and reports whether
     * the caller is still within the allowed rate.
     *
     * @return {@code true} if the request may proceed, {@code false} if this IP
     *         has exceeded the allowed number of attempts in the current window
     */
    public boolean tryAcquire(String ipAddress) {
        String key = ipAddress == null ? "unknown" : ipAddress;
        AtomicInteger count = attemptsByIp.get(key, k -> new AtomicInteger(0));
        return count.incrementAndGet() <= maxAttemptsPerWindow;
    }
}
