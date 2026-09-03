package com.hirewise.be.ai;

import java.util.List;

/**
 * Structured result of one AI Screening analysis (UC-21) - what
 * {@link MatchingEngine#analyze} returns and what
 * {@code service.AiScreeningService} persists into
 * {@code domain.AiScreeningRun}/{@code domain.AiSkillMatch}.
 *
 * @param matchScore     overall match percentage, 0-100 (BR-AI-03 thresholds)
 * @param summary        2-3 sentence AI-generated summary of strengths/gaps
 * @param matchedSkills  JD-required skills the CV demonstrates
 * @param missingSkills  JD-required skills the CV does not demonstrate
 */
public record MatchAnalysisResult(
        int matchScore,
        String summary,
        List<String> matchedSkills,
        List<String> missingSkills) {
}
