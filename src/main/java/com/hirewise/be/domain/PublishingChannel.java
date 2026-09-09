package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One external destination a Job Position can be shared to (UC-19), seeded in
 * {@code V39} and edited by HR Admin.
 *
 * <p>Rows are configuration, not user data: the four channels ship with the
 * product and HR Admin only toggles them on/off and adjusts {@code utmSource}.
 * There is deliberately no create/delete API - adding a fifth network means a
 * new {@link PublishingChannelCode} constant and a migration, because each
 * channel also needs its own share-intent URL shape.</p>
 */
@Entity
@Table(name = "publishing_channels")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PublishingChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "publishing_channel_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, unique = true)
    private PublishingChannelCode code;

    @Column(nullable = false, length = 100)
    private String name;

    /**
     * The platform share-intent URL, with {@code {url}} standing in for the
     * URL-encoded share link. {@code null} for {@link PublishingChannelCode#COPY_LINK},
     * which the frontend handles with the clipboard instead of a popup.
     */
    @Column(name = "share_intent_url_template", columnDefinition = "text")
    private String shareIntentUrlTemplate;

    /** Matched against {@code applications.source} to attribute candidates (UC-32). */
    @Column(name = "utm_source", nullable = false, length = 50)
    private String utmSource;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
