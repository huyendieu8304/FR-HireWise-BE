package com.hirewise.be.dto.response;

import com.hirewise.be.domain.ScorecardStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** HR Admin's Master Scorecard Template library (UC-27) - always company-wide. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScorecardTemplateResponseDto {
    private UUID id;
    private String name;
    private ScorecardStatus status;
    private List<ScorecardCriterionResponseDto> criteria;
    private Instant createdAt;
    private Instant updatedAt;
}
