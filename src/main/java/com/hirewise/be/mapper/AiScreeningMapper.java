package com.hirewise.be.mapper;

import com.hirewise.be.domain.AiMatchType;
import com.hirewise.be.domain.AiScreeningRun;
import com.hirewise.be.domain.AiSkillMatch;
import com.hirewise.be.dto.response.AiScreeningResultResponseDto;

import java.util.List;

/** UC-21: pure Entity -> DTO conversion, no query/service call (CODING_CONVENTION.md mục 3). */
public final class AiScreeningMapper {

    private AiScreeningMapper() {
    }

    public static AiScreeningResultResponseDto toDto(AiScreeningRun run, List<AiSkillMatch> skillMatches) {
        return AiScreeningResultResponseDto.builder()
                .runId(run.getId())
                .status(run.getStatus())
                .matchScore(run.getMatchScore())
                .summary(run.getSummary())
                .matchedSkills(skillMatches.stream()
                        .filter(m -> m.getMatchType() == AiMatchType.MATCHED)
                        .map(AiSkillMatch::getSkillName)
                        .toList())
                .missingSkills(skillMatches.stream()
                        .filter(m -> m.getMatchType() == AiMatchType.MISSING)
                        .map(AiSkillMatch::getSkillName)
                        .toList())
                .errorMessage(run.getErrorMessage())
                .createdAt(run.getCreatedAt())
                .completedAt(run.getCompletedAt())
                .build();
    }
}
