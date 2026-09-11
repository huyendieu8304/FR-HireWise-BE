package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScorecardCriterionResponseDto {
    private UUID id;
    private String name;
    private String description;
    private BigDecimal weight;
    private BigDecimal maxScore;
    private int position;
    private boolean required;
}
