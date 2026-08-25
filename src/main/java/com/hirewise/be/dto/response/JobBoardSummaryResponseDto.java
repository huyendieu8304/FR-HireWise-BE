package com.hirewise.be.dto.response;

import com.hirewise.be.domain.EmploymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * UC-16 step 2: one card in the public Job Board list. Deliberately a
 * smaller projection than {@link JobBoardDetailResponseDto} - the list view
 * never needs the full JD text (description/requirements/benefits).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobBoardSummaryResponseDto {
    private UUID id;
    private String title;
    private String departmentName;
    private EmploymentType employmentType;
    private String location;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private Instant createdAt;
}
