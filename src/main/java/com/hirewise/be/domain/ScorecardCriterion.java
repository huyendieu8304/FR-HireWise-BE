package com.hirewise.be.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One evaluation criterion (row) of a {@link ScorecardTemplate} (UC-27) -
 * e.g. "Technical depth", weight 40, max score 5, required.
 */
@Entity
@Table(name = "scorecard_criteria")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScorecardCriterion {

    @Id
    @Column(name = "criterion_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scorecard_template_id", nullable = false)
    private ScorecardTemplate scorecardTemplate;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    /** BR-SCORE-01/02: relative importance in the Weighted Score formula - not required to sum to 100. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(name = "max_score", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxScore;

    /** Display order within the template - assigned by insertion order in the config screen. */
    @Column(nullable = false)
    private int position;

    /** BR-SCORE-01: a required criterion must be scored (and the overall comment non-blank) before Submit. */
    @Column(name = "is_required", nullable = false)
    private boolean required;
}
