package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Reusable body of an offer letter/contract (UC-36 step 2), with
 * {@code {{Placeholder}}} variables filled in per offer - the same
 * placeholder syntax {@link EmailTemplate} already uses.
 * <p>
 * {@link #version} exists so wording changes never rewrite an offer that
 * was already rendered: a new version row is inserted and the old one set
 * {@code INACTIVE}, while every {@link Offer} keeps its own rendered
 * snapshot in {@code offers.rendered_body}.
 */
@Entity
@Table(name = "offer_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OfferTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "offer_template_id")
    private Long id;

    /** {@code null} for a company-wide template usable by every department. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "body_template", nullable = false, columnDefinition = "text")
    private String bodyTemplate;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OfferTemplateStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Whether this template version may still be picked in UC-36's dropdown. */
    public boolean isActive() {
        return status == OfferTemplateStatus.ACTIVE;
    }
}
