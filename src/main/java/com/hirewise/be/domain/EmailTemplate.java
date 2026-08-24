package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A versioned, optionally stage-triggered email template (UC-09/UC-10/UC-11).
 * {@code code} is unique system-wide (BR-EMAILTPL-01 - unlike
 * {@code pipeline_stages.code}, which is only unique per template).
 * {@link #version} increments on every content edit (BR-EMAILTPL-04); already
 * sent emails keep their own rendered copy elsewhere ({@code email_messages},
 * out of scope through UC-17) and are unaffected by later edits here.
 */
@Entity
@Table(name = "email_templates")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmailTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "email_template_id")
    private Long id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    /** Optional; auto-sent when an Application enters this Stage (BR-EMAILTPL-01). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_stage_id")
    private PipelineStage pipelineStage;

    /** May contain dynamic variables like {@code {{Candidate_Name}}} (BR-EMAILTPL-02). */
    @Column(name = "subject_template", nullable = false, length = 255)
    private String subjectTemplate;

    @Column(name = "body_template", nullable = false, columnDefinition = "text")
    private String bodyTemplate;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailTemplateStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
