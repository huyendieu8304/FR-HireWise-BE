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
 * "Vị trí tuyển dụng" internal detail ({@code JOB_VIEW}) - tab "Mô tả chi
 * tiết" khi Recruiter/Hiring Manager mở 1 Job từ danh sách. Khác
 * {@link JobApprovalDetailResponseDto} (UC-15, chỉ dùng trong luồng phê
 * duyệt và mang thêm danh sách Pipeline Stage) ở chỗ DTO này phục vụ xem
 * chi tiết JD nói chung, ở bất kỳ trạng thái Job nào.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobDetailResponseDto {

    private UUID id;
    private String title;
    private Long departmentId;
    private String departmentName;
    private String location;
    private EmploymentType employmentType;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private int openings;
    private LocalDate applicationDeadline;
    private String description;
    private String requirements;
    private String benefits;
    private JobStatus status;

    /** Tên đầy đủ của Recruiter sở hữu job; {@code null} nếu chưa được gán. */
    private String recruiterName;

    /** Tên đầy đủ của Hiring Manager duyệt job; {@code null} nếu chưa được gán. */
    private String hiringManagerName;

    private Long pipelineTemplateId;
    private String pipelineTemplateName;

    private Instant createdAt;
    private Instant updatedAt;
}
