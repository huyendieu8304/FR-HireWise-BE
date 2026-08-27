package com.hirewise.be.dto.response;

import com.hirewise.be.domain.EmploymentType;
import com.hirewise.be.domain.JobStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * UC-15 normal flow step 1: full job position detail shown to the Hiring
 * Manager when they open a pending-approval request to make a decision.
 * <p>
 * Includes all three JD blocks (description/requirements/benefits from UC-12),
 * compensation info (salary range), and headcount (openings) so the manager
 * has everything they need without a second API call.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobApprovalDetailResponseDto {

    /** UUID of the job position — echoed back so the frontend can correlate. */
    private UUID id;

    /** Chức danh tuyển dụng. */
    private String title;

    /** Tên phòng ban sở hữu job. */
    private String departmentName;

    /** Số lượng chỉ tiêu (BR-JOB-01: >= 1). */
    private int openings;

    /** Loại hình lao động. */
    private EmploymentType employmentType;

    /** Địa điểm làm việc. */
    private String location;

    /** Mức lương tối thiểu (null = thương lượng). */
    private BigDecimal salaryMin;

    /** Mức lương tối đa (null = thương lượng). */
    private BigDecimal salaryMax;

    /** Hạn nộp hồ sơ (null = không giới hạn). */
    private LocalDate applicationDeadline;

    /** Block mô tả công việc (UC-12). */
    private String description;

    /** Block yêu cầu ứng viên (UC-12). */
    private String requirements;

    /** Block quyền lợi (UC-12). */
    private String benefits;

    /** Tên đầy đủ của Recruiter đã tạo job. */
    private String createdByUserName;

    /**
     * Thời điểm Recruiter submit job lên chờ duyệt (UC-13).
     * Mapped from {@code JobPosition.updatedAt} — updated each time the job
     * transitions to PENDING_APPROVAL, even after a resubmit-after-rejection.
     */
    private Instant submittedAt;

    /**
     * Trạng thái hiện tại của Job Position.
     */
    private JobStatus status;

    /** ID của quy trình tuyển dụng (Pipeline Template) được gán cho job. */
    private Long pipelineTemplateId;

    /** Tên quy trình tuyển dụng. */
    private String pipelineTemplateName;

    /** Danh sách các bước (stages) trong quy trình tuyển dụng, sắp xếp theo thứ tự position. */
    private java.util.List<PipelineStageResponseDto> pipelineStages;
}

