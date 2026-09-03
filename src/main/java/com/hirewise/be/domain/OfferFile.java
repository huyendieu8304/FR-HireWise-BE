package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Links a {@link StoredFile} to the {@link Offer} it belongs to (UC-39 step
 * 4), tagged with what the artifact represents. The Offer counterpart of
 * {@link ApplicationFile}.
 */
@Entity
@Table(name = "offer_files")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfferFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "offer_file_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id", nullable = false)
    private Offer offer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
    private StoredFile file;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_role", nullable = false, length = 30)
    private OfferFileRole fileRole;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
