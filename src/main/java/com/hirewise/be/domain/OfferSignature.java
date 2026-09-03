package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Evidence that a Candidate electronically signed an {@link Offer} (UC-39,
 * BR-OFFER-04) - not merely a flag, which is why it is its own table:
 * {@link #method}, {@link #signerName}, {@link #otpVerifiedAt},
 * {@link #signedAt} and {@link #ipAddress} together are what makes the
 * signature defensible after the fact.
 * <p>
 * {@link #otpVerifiedAt} is copied off the offer access token at signing
 * time rather than read through it later: that token row is operational
 * state which may be cleaned up, whereas this is a record.
 */
@Entity
@Table(name = "offer_signatures")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfferSignature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "signature_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false)
    private Offer offer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "signer_candidate_id", nullable = false)
    private Candidate signerCandidate;

    /** {@code null} while the signed PDF is still queued locally (BR-STORAGE-02). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "signed_file_id")
    private StoredFile signedFile;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SignatureMethod method;

    @Column(name = "signer_name", nullable = false, length = 150)
    private String signerName;

    @Column(name = "otp_verified_at")
    private Instant otpVerifiedAt;

    @Column(name = "signed_at", nullable = false)
    private Instant signedAt;

    /** Mapped as text; PostgreSQL casts it into the {@code inet} column. */
    @Column(name = "ip_address", columnDefinition = "inet")
    private String ipAddress;
}
