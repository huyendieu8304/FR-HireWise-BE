package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Immutable log of every stage change an {@link Application} goes through.
 * {@link #fromStage} is {@code null} for the very first event (recorded when
 * the Application is created, UC-17); {@link #changedBy} is {@code null} when
 * an automation/system rule performed the move rather than a person.
 */
@Entity
@Table(name = "application_stage_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationStageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_stage_history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    /** {@code null} for the first event of an Application. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_stage_id")
    private PipelineStage fromStage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_stage_id", nullable = false)
    private PipelineStage toStage;

    /** {@code null} when an automation/system rule performed the transition. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by_user_id")
    private User changedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "transition_type", nullable = false, length = 20)
    private StageTransitionType transitionType;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;
}
