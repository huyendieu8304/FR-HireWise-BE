package com.hirewise.be.dto.response;

import com.hirewise.be.domain.EmploymentType;
import com.hirewise.be.domain.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * "Vị trí tuyển dụng" internal list ({@code JOB_VIEW}, sidebar mục "Vị trí
 * tuyển dụng"): one row - every Job Position visible to the caller
 * regardless of status, unlike the public Job Board (PUBLISHED-only) or the
 * approval list (approval-relevant statuses only, gated by {@code JOB_APPROVE}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobSummaryResponseDto {

    private UUID id;
    private String title;
    private Long departmentId;
    private String departmentName;
    private JobStatus status;
    private EmploymentType employmentType;
    private int openings;

    /** Tên đầy đủ của Recruiter sở hữu job; {@code null} nếu job chưa được gán Recruiter. */
    private String recruiterName;

    private Instant createdAt;
}
