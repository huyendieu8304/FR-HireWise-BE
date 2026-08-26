package com.hirewise.be.dto.response;

import com.hirewise.be.domain.EmploymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * UC-14: One row in the Hiring Manager's "pending approval" list.
 * <p>
 * Carries exactly the fields displayed in the normal flow step 3:
 * <ul>
 *   <li>Chức danh — {@link #title}</li>
 *   <li>Phòng ban — {@link #departmentName}</li>
 *   <li>Số lượng chỉ tiêu — {@link #openings}</li>
 *   <li>Người tạo — {@link #createdByUserName}</li>
 *   <li>Ngày gửi — {@link #submittedAt}</li>
 * </ul>
 * Plus {@link #employmentType} for a badge on the list row.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PendingApprovalJobSummaryResponseDto {

    /** UUID of the job position — used by UC-15 when the manager clicks a row. */
    private UUID id;

    /** Chức danh tuyển dụng. */
    private String title;

    /** Tên phòng ban sở hữu job (null nếu job chưa gán phòng ban — không xảy ra với PENDING_APPROVAL). */
    private String departmentName;

    /** Số lượng chỉ tiêu tuyển dụng (BR-JOB-01: >= 1). */
    private int openings;

    /** Loại hình lao động — dùng để hiển thị badge trên UI. */
    private EmploymentType employmentType;

    /**
     * Tên đầy đủ của người tạo job (Recruiter). Resolved from
     * {@code JobPosition.recruiter.fullName}; {@code null} if the recruiter
     * record no longer exists (deleted user scenario).
     */
    private String createdByUserName;

    /**
     * Thời điểm Recruiter submit job lên để chờ duyệt (UC-13).
     * Mapped from {@code JobPosition.updatedAt} — the timestamp is updated
     * every time the job transitions to PENDING_APPROVAL, so it represents
     * the most recent submission time even after a resubmit-after-rejection
     * cycle.
     */
    private Instant submittedAt;
}
