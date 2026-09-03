package com.hirewise.be.security.token;

import com.hirewise.be.domain.Offer;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * The candidate's secure link to one {@link Offer} (UC-37 step 2) plus the
 * OTP state guarding it (UC-38, BR-OFFER-03).
 * <p>
 * Candidates have no account in this system (SRS section 3.1), so this row
 * is the whole authentication story for UC-38/UC-39: possession of the
 * emailed link, then a verified one-time code. Only the hash of the token's
 * secret half and of the OTP are persisted - see {@link OpaqueTokenUtil}
 * and {@link ActivationToken}, which use the same scheme.
 */
@Entity
@Table(name = "offer_access_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfferAccessToken {

    @Id
    @Column(name = "token_id")
    private UUID tokenId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false)
    private Offer offer;

    @Column(name = "token_hash", nullable = false, length = 255)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "otp_code_hash", length = 255)
    private String otpCodeHash;

    @Column(name = "otp_expires_at")
    private Instant otpExpiresAt;

    /** Wrong-code counter; reset each time a fresh OTP is issued (ME-33). */
    @Column(name = "otp_attempts", nullable = false)
    private int otpAttempts;

    @Column(name = "otp_sent_count", nullable = false)
    private int otpSentCount;

    @Column(name = "otp_last_sent_at")
    private Instant otpLastSentAt;

    /** UC-38 postcondition - the only field that flow writes. */
    @Column(name = "otp_verified_at")
    private Instant otpVerifiedAt;

    /** Set once the Offer is signed (UC-39), retiring the link. */
    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Whether the link itself is still usable, ignoring OTP state. */
    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    /**
     * Whether the candidate may currently read the offer's terms: OTP
     * verified, and still inside the post-verification viewing window.
     *
     * @param now             current instant
     * @param viewWindowMinutes how long one OTP verification stays good for
     */
    public boolean isViewAllowed(Instant now, long viewWindowMinutes) {
        return otpVerifiedAt != null
                && otpVerifiedAt.plusSeconds(viewWindowMinutes * 60).isAfter(now);
    }
}
