package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A reusable recruitment workflow definition (UC-04), made of an ordered list
 * of {@link PipelineStage}s. Can be company-wide ({@link #department} is
 * {@code null}) or scoped to one department.
 */
@Entity
@Table(name = "pipeline_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pipeline_template_id")
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    /** {@code null} = shared by every department (UC-04 AF-01). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    /** BR-PIPE-01 must hold before this can move from DRAFT to ACTIVE. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PipelineTemplateStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
