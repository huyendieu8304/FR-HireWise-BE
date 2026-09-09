package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * How one Job Position has performed on one sharing channel (UC-31/UC-32).
 *
 * <p>BR-POST-02 says a (Job, Channel) pair may only ever have one active
 * record, which the {@code uk_job_posting_channels_job_channel} unique
 * constraint enforces: sharing the same job to LinkedIn a second time
 * increments {@link #shareCount} on the existing row rather than inserting a
 * duplicate.</p>
 *
 * <p>The original SRS modelled this table with a
 * {@code Processing/Success/Failed} status (LV-31), which only made sense
 * while the system posted through the provider APIs itself. With share-intent
 * links there is no asynchronous call to be in the middle of, so the status
 * column is replaced by the two counters plus the timestamps below.</p>
 */
@Entity
@Table(name = "job_posting_channels")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobPostingChannel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_posting_channel_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_position_id", nullable = false)
    private JobPosition jobPosition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "publishing_channel_id", nullable = false)
    private PublishingChannel channel;

    /** Times a Recruiter pressed [Chia se] for this pair. */
    @Column(name = "share_count", nullable = false)
    private int shareCount;

    /**
     * Times a real person opened the share link. Known crawler user agents are
     * filtered out in {@code PublicJobShareController} - otherwise Facebook and
     * LinkedIn fetching the page for its Open Graph preview would inflate this
     * the moment anything is shared.
     */
    @Column(name = "click_count", nullable = false)
    private int clickCount;

    @Column(name = "first_shared_at")
    private Instant firstSharedAt;

    @Column(name = "last_shared_at")
    private Instant lastSharedAt;

    @Column(name = "last_shared_by_user_id")
    private Long lastSharedByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Convenience for callers that only need the id without initialising the lazy proxy. */
    public UUID getJobPositionId() {
        return jobPosition != null ? jobPosition.getId() : null;
    }
}
