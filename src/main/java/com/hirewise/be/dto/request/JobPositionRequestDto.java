package com.hirewise.be.dto.request;

import com.hirewise.be.domain.EmploymentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Request body for {@code JOB_CREATE} (UC-12 normal flow) and
 * {@code JOB_EDIT} (UC-12 AF-01 - "Lưu nháp" bất kỳ lúc nào on an existing
 * Draft/Rejected job) - same field set for both actions since re-saving a
 * draft resends the whole form, not a partial patch.
 * <p>
 * Only the fields the Screen Description marks "Bắt buộc" for saving a
 * Draft are required here (title, department, openings) - the fuller
 * BR-JOB-01 checklist (+ employment type + Pipeline Template) is only
 * enforced when submitting for approval (UC-13), a separate action not
 * implemented by this DTO/endpoint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobPositionRequestDto {

    @NotBlank(message = "{validation.job_position.title.required}")
    @Size(max = 120, message = "{validation.job_position.title.size}")
    private String title;

    @NotNull(message = "{validation.job_position.department_id.required}")
    private Long departmentId;

    /** Optional at Draft-save time (BR-JOB-01 only requires it at UC-13 submit). */
    private EmploymentType employmentType;

    /** Optional; {@code null} on both = "Thỏa thuận". BR-JOB-02: min &lt;= max when both are set. */
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;

    @NotNull(message = "{validation.job_position.openings.required}")
    @Positive(message = "{validation.job_position.openings.positive}")
    private Integer openings;

    /** Optional; BR-JOB-03: must be a future date when set. */
    private LocalDate applicationDeadline;

    /** Optional; shown on the public Job Board later (UC-16), not part of the UC-12 Screen Description. */
    private String location;

    /** JD block 1/3 - "Mô tả công việc". Not required to save a Draft (only at UC-13 submit). */
    private String description;

    /** JD block 2/3 - "Yêu cầu ứng viên". */
    private String requirements;

    /** JD block 3/3 - "Quyền lợi". */
    private String benefits;
}
