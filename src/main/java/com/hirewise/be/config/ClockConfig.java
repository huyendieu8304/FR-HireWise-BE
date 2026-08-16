package com.hirewise.be.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Bean Clock dùng trong service thay vì gọi thẳng Instant.now()/LocalDateTime.now(),
 * để dễ mock thời gian khi viết unit test.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
