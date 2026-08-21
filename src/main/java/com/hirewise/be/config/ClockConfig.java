package com.hirewise.be.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Exposes a {@link Clock} bean so services obtain the current time through
 * dependency injection instead of calling {@code Instant.now()} /
 * {@code LocalDateTime.now()} directly, making it possible to inject a
 * fixed or mocked clock in unit tests.
 */
@Configuration
public class ClockConfig {

    /**
     * @return a UTC-based system clock used as the single source of "now"
     *         across the application
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
