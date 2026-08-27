package com.hirewise.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UC-15 AF-01: request body for {@code POST /api/job-approvals/{jobId}/reject}.
 * <p>
 * BR-APR-02: a rejection reason is mandatory and must be at least 10 characters
 * so the Recruiter receives actionable feedback rather than a blank notification.
 * EX-01: validation failure returns HTTP 400 with field error "reason".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RejectJobRequestDto {

    /**
     * Lý do từ chối — bắt buộc, tối thiểu 10 ký tự (BR-APR-02 / ME-21).
     * Recruiter sẽ nhận được lý do này trong email EM-03.
     */
    @NotBlank(message = "Lý do từ chối không được để trống.")
    @Size(min = 10, message = "Lý do từ chối phải có ít nhất 10 ký tự (ME-21).")
    private String reason;
}
