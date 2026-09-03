package com.hirewise.be.dto.response;

import com.hirewise.be.domain.AiScreeningStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * UC-21: the latest AI Screening Run for one Application, shown on the
 * [AI Match Analysis] tab of the Applicant Card. {@code matchScore}/
 * {@code summary}/skill lists are {@code null} while {@code status} is
 * {@code PENDING} ("Đang xử lý") or {@code FAILED} (EX-01, {@code errorMessage}
 * carries why) - only a {@code SUCCEEDED} run has them populated.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiScreeningResultResponseDto {

    private Long runId;
    private AiScreeningStatus status;
    private BigDecimal matchScore;
    private String summary;
    private List<String> matchedSkills;
    private List<String> missingSkills;
    private String errorMessage;
    private Instant createdAt;
    private Instant completedAt;
}
