package com.hirewise.be.dto.response;

import com.hirewise.be.domain.RejectionCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UC-29 step 1: one choice in the Recruiter's reject-reason dropdown
 * ({@code GET /api/rejection-reasons}, BR-REJ-01).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RejectionReasonResponseDto {
    private Long id;
    private String code;
    private String label;
    private RejectionCategory category;
}
