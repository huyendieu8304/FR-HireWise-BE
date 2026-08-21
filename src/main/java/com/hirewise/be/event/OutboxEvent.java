package com.hirewise.be.event;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Transactional outbox row (UC-02 postcondition: "he thong enqueue
 * outbox_event gui email EM-01"). Writing this row happens in the SAME
 * DB transaction as the business change that triggered it (e.g. creating
 * a user), so the email is never "lost" due to a crash between saving the
 * business row and actually sending the mail - a separate poller
 * ({@code event.OutboxDispatcher}) picks up {@code PENDING} rows and
 * sends them asynchronously, independent of the original request/transaction.
 * <p>
 * {@code payload} is a small JSON blob with just enough data to render the
 * email (e.g. {@code {"userId":1,"email":"...","fullName":"...","token":"..."}}) -
 * kept as a plain string so this table doesn't need to know about every
 * possible email's shape.
 */
@Entity
@Table(name = "outbox_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_event_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private OutboxEventType eventType;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;
}
