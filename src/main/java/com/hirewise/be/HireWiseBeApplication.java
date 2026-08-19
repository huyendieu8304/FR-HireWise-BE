package com.hirewise.be;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point that bootstraps the HireWise backend Spring Boot
 * application.
 * <p>
 * {@code @EnableScheduling} activates {@code event.OutboxDispatcher}'s
 * {@code @Scheduled} poller, which is what actually sends the emails
 * enqueued by {@code event.OutboxEventPublisher} (activation links,
 * BR-AUTH-02 security alerts).
 */
@SpringBootApplication
@EnableScheduling
public class HireWiseBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(HireWiseBeApplication.class, args);
    }

}
