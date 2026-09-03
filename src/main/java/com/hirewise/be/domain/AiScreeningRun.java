package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * UC-21: one AI Screening attempt for an {@link Application} - queued as
 * {@code PENDING} right when the CV is attached (or on AF-01 manual
 * re-run), then picked up asynchronously by
 * {@code event.AiScreeningDispatcher} which calls the Claude API
 * ({@code ai.MatchingEngine}) and fills in the result. Every past run is
 * kept (never overwritten) so re-running after a JD/CV update still has
 * full audit history (BR-AI-02).
 */
@Entity
@Table(name = "ai_screening_runs")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiScreeningRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "run_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    /** BR-AI-02: which model produced this run's result, e.g. {@code "claude-haiku-4-5"}. */
    @Column(name = "model_name", nullable = false, length = 50)
    private String modelName;

    /** BR-AI-02: version of the prompt/schema used - bump when the analysis prompt changes materially. */
    @Column(name = "prompt_version", nullable = false, length = 20)
    private String promptVersion;

    /** Match Score percentage (0-100). {@code null} while PENDING or if the run FAILED. */
    @Column(name = "match_score")
    private BigDecimal matchScore;

    /** AI-generated summary paragraph. {@code null} while PENDING or if the run FAILED. */
    @Column(name = "summary")
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AiScreeningStatus status;

    /** EX-01: populated only when {@link #status} is {@code FAILED}. */
    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;
}
