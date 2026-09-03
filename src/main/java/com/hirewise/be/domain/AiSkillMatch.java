package com.hirewise.be.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One skill line in a {@link AiScreeningRun}'s breakdown (UC-21 step 3) -
 * either a skill the JD requires and the CV shows ({@link AiMatchType#MATCHED})
 * or one the JD requires but the CV doesn't show ({@link AiMatchType#MISSING}).
 * Stored as plain text ({@link #skillName}), not a normalized skill catalog -
 * see {@code guides/04-DATABASE_DESIGN.md} mục 13.
 */
@Entity
@Table(name = "ai_skill_matches")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiSkillMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "match_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private AiScreeningRun run;

    @Column(name = "skill_name", nullable = false, length = 100)
    private String skillName;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 20)
    private AiMatchType matchType;
}
