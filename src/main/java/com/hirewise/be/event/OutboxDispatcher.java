package com.hirewise.be.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hirewise.be.service.EmailService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Polls {@code outbox_events} for {@code PENDING} rows and actually sends
 * the corresponding email through {@link EmailService}, independent of
 * whatever HTTP request originally enqueued them (see
 * {@code event.OutboxEvent} / {@link OutboxEventPublisher}).
 * <p>
 * A row that fails to send is marked {@code FAILED} with an attempt count
 * and error message rather than retried forever in a tight loop - past
 * {@code app.outbox.max-attempts} it is left for manual/ops follow-up
 * (e.g. "resend activation email" - out of scope for this MVP) instead of
 * silently dropping it.
 */
@Slf4j
@Component
public class OutboxDispatcher {

    private final OutboxEventRepository outboxEventRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final int batchSize;
    private final int maxAttempts;

    public OutboxDispatcher(OutboxEventRepository outboxEventRepository,
                             EmailService emailService,
                             ObjectMapper objectMapper,
                             Clock clock,
                             @Value("${app.outbox.batch-size:20}") int batchSize,
                             @Value("${app.outbox.max-attempts:5}") int maxAttempts) {
        this.outboxEventRepository = outboxEventRepository;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:5000}")
    public void dispatchPendingEvents() {
        List<OutboxEvent> batch = outboxEventRepository.findBatchByStatus(OutboxEventStatus.PENDING, PageRequest.of(0, batchSize));
        for (OutboxEvent event : batch) {
            dispatchOne(event);
        }
    }

    @Transactional
    void dispatchOne(OutboxEvent event) {
        try {
            JsonNode payload = objectMapper.readTree(event.getPayload());
            switch (event.getEventType()) {
                case ACTIVATION_EMAIL -> emailService.sendActivationEmail(
                        requireField(payload, "email", event.getEventType()),
                        payload.path("fullName").asText(null),
                        requireField(payload, "activationLink", event.getEventType()));
                case SECURITY_ALERT_EMAIL -> emailService.sendSecurityAlertEmail(
                        requireField(payload, "email", event.getEventType()),
                        payload.path("fullName").asText(null),
                        payload.path("ipAddress").asText(null));
            }
            event.setStatus(OutboxEventStatus.SENT);
            event.setProcessedAt(Instant.now(clock));
            event.setErrorMessage(null);
        } catch (Exception e) {
            int attempts = event.getAttempts() + 1;
            event.setAttempts(attempts);
            event.setErrorMessage(e.getMessage());
            if (attempts >= maxAttempts) {
                event.setStatus(OutboxEventStatus.FAILED);
                log.error("Outbox event {} ({}) permanently failed after {} attempts: {}",
                        event.getId(), event.getEventType(), attempts, e.getMessage());
            } else {
                // Leave as PENDING so the next poll retries it.
                log.warn("Outbox event {} ({}) failed on attempt {}/{}: {}",
                        event.getId(), event.getEventType(), attempts, maxAttempts, e.getMessage());
            }
        }
        outboxEventRepository.save(event);
    }

    /**
     * Reads a required string field out of a deserialized outbox payload, failing
     * loudly instead of silently sending an email with a blank field.
     * <p>
     * {@link OutboxPayloads} is the single place each event type's payload is
     * built, but nothing stops a future publisher from bypassing it or a payload
     * shape from drifting - this is the safety net on the read side: a missing or
     * blank required field throws here, gets caught by {@link #dispatchOne},
     * and marks the row {@code FAILED} with a clear message instead of quietly
     * producing a broken email.
     *
     * @param payload   the deserialized outbox event payload
     * @param field     name of the required JSON field
     * @param eventType the event type being dispatched, for the error message
     * @return the field's text value
     * @throws IllegalStateException if the field is missing, null, or blank
     */
    private static String requireField(JsonNode payload, String field, OutboxEventType eventType) {
        String value = payload.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    "Outbox payload for " + eventType + " is missing required field '" + field + "'");
        }
        return value;
    }
}
