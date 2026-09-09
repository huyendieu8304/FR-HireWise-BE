package com.hirewise.be.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * UC-44 step 2: request body for {@code POST /api/jobs/{jobId}/pause} and
 * {@code POST /api/jobs/{jobId}/close} - the Confirm Modal's optional reason
 * field.
 *
 * <p>Unlike {@code RejectJobRequestDto}, where BR-APR-02 makes the reason
 * mandatory because the Recruiter has to act on it, the UC-44 reason is
 * explicitly "(và lý do, tuỳ chọn)" in the SRS: nobody is blocked waiting on
 * it, so an empty body is a valid request. It is stored in
 * {@code audit_logs.after_json} rather than on the Job itself.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobLifecycleRequestDto {

    /** Lý do Tạm dừng / Đóng vị trí — tuỳ chọn, chỉ để tra cứu về sau. */
    @Size(max = 500, message = "{validation.job_lifecycle.reason.size}")
    private String reason;
}
