package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One Kanban column inside a {@link PipelineTemplate}.
 * {@code code} is unique per template (BR-PIPE-02, not system-wide);
 * {@code position} must stay a contiguous, ascending sequence within the
 * template (BR-PIPE-04) - reordering (UC-05) rewrites every affected row's
 * position in one transaction.
 */
@Entity
@Table(name = "pipeline_stages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineStage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pipeline_stage_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pipeline_template_id", nullable = false)
    private PipelineTemplate pipelineTemplate;

    @Column(nullable = false, length = 50)
    private String name;

    /** Stable technical code, unique within {@link #pipelineTemplate} (BR-PIPE-02). */
    @Column(nullable = false, length = 50)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_type", nullable = false, length = 20)
    private StageType stageType;

    /** Display/Kanban column order within the template (BR-PIPE-04). */
    @Column(nullable = false)
    private int position;

    /** True for the stage(s) that end the pipeline (BR-PIPE-01, BR-PIPE-03). */
    @Column(name = "is_terminal", nullable = false)
    private boolean terminal;

    /** Optional max hours an Application may sit in this stage (SLA Monitoring, module M19). */
    @Column(name = "sla_hours")
    private Integer slaHours;

    /** Soft-delete flag (UC-06); preferred over a hard delete to keep application_stage_history intact. */
    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
