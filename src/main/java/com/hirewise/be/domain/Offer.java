package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * One offer letter made to a Candidate for a specific {@link Application}
 * (UC-36). Created as {@link OfferStatus#DRAFT}, moved to {@code SENT} by
 * UC-37 and to {@code SIGNED} by UC-39.
 * <p>
 * {@link #renderedBody} is the offer's own frozen copy of the template body
 * with every placeholder already substituted, deliberately not re-rendered
 * on read: the candidate must see exactly the text that existed when the
 * offer was created, even if the template or the salary field changes
 * afterwards, and BR-OFFER-04 requires the signed content to be immutable.
 */
@Entity
@Table(name = "offers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Offer {

    /**
     * Statuses that make an Offer "active" for BR-OFFER-01 - an Application
     * may have at most one Offer in one of these at a time. Also enforced by
     * the partial unique index {@code uk_offers_one_active_per_application}.
     */
    public static final Set<OfferStatus> ACTIVE_STATUSES =
            EnumSet.of(OfferStatus.DRAFT, OfferStatus.SENT);

    @Id
    @Column(name = "offer_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_template_id", nullable = false)
    private OfferTemplate offerTemplate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal salary;

    /** Percentage of {@link #salary} paid during probation; {@code null} = not specified. */
    @Column(name = "probation_rate", precision = 5, scale = 2)
    private BigDecimal probationRate;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    /** BR-OFFER-02: answer deadline; past this the Offer auto-moves to {@code EXPIRED}. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OfferStatus status;

    @Column(name = "rendered_body", nullable = false, columnDefinition = "text")
    private String renderedBody;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** BR-OFFER-01: whether this Offer still blocks creating another one. */
    public boolean isActive() {
        return ACTIVE_STATUSES.contains(status);
    }
}
