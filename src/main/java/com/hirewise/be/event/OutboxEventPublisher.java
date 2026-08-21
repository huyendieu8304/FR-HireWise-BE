package com.hirewise.be.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/**
 * Enqueues an {@link OutboxEvent} row. Callers invoke this from WITHIN the
 * same {@code @Transactional} method that made the business change the
 * email is about (e.g. {@code UserAdminService#create}), so the row commits
 * atomically together with that change - see {@code event.OutboxEvent}
 * for why this beats sending the email synchronously.
 */
@Component
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxEventPublisher(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper, Clock clock) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @SneakyThrows
    public void publish(OutboxEventType type, Map<String, Object> payload) {
        OutboxEvent event = OutboxEvent.builder()
                .eventType(type)
                .payload(objectMapper.writeValueAsString(payload))
                .status(OutboxEventStatus.PENDING)
                .attempts(0)
                .createdAt(Instant.now(clock))
                .build();
        outboxEventRepository.save(event);
    }
}
