package com.hirewise.be.dto.response;

import com.hirewise.be.domain.EmploymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * UC-16 step 4: full public JD for a single Published job position
 * (Description / Requirements / Benefits blocks, per the 3-block JD from
 * UC-12), plus what the apply form (UC-17) needs to know about the job.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobBoardDetailResponseDto {
    private UUID id;
    private String title;
    private String departmentName;
    private EmploymentType employmentType;
    private String location;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private int openings;
    private LocalDate applicationDeadline;
    private String description;
    private String requirements;
    private String benefits;
    private Instant createdAt;
}
