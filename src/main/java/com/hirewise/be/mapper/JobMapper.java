package com.hirewise.be.mapper;

import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.dto.response.JobDetailResponseDto;
import com.hirewise.be.dto.response.JobSummaryResponseDto;

/**
 * Converts {@link JobPosition} entities into the "Vị trí tuyển dụng"
 * internal list/detail response DTOs ({@code JOB_VIEW}, see {@link
 * com.hirewise.be.service.JobService}).
 */
public final class JobMapper {

    private JobMapper() {
    }

    /**
     * Converts a {@link JobPosition} into its list-row summary DTO.
     *
     * @param job entity to convert
     * @return the corresponding summary DTO
     */
    public static JobSummaryResponseDto toSummaryDto(JobPosition job) {
        return JobSummaryResponseDto.builder()
                .id(job.getId())
                .title(job.getTitle())
                .departmentId(job.getDepartment() != null ? job.getDepartment().getId() : null)
                .departmentName(job.getDepartment() != null ? job.getDepartment().getName() : null)
                .status(job.getStatus())
                .employmentType(job.getEmploymentType())
                .openings(job.getOpenings())
                .recruiterName(job.getRecruiter() != null ? job.getRecruiter().getFullName() : null)
                .createdAt(job.getCreatedAt())
                .build();
    }

    /**
     * Converts a {@link JobPosition} into its full detail DTO.
     *
     * @param job entity to convert
     * @return the corresponding detail DTO
     */
    public static JobDetailResponseDto toDetailDto(JobPosition job) {
        return JobDetailResponseDto.builder()
                .id(job.getId())
                .title(job.getTitle())
                .departmentId(job.getDepartment() != null ? job.getDepartment().getId() : null)
                .departmentName(job.getDepartment() != null ? job.getDepartment().getName() : null)
                .location(job.getLocation())
                .employmentType(job.getEmploymentType())
                .salaryMin(job.getSalaryMin())
                .salaryMax(job.getSalaryMax())
                .openings(job.getOpenings())
                .applicationDeadline(job.getApplicationDeadline())
                .description(job.getDescription())
                .requirements(job.getRequirements())
                .benefits(job.getBenefits())
                .status(job.getStatus())
                .recruiterName(job.getRecruiter() != null ? job.getRecruiter().getFullName() : null)
                .hiringManagerName(job.getHiringManager() != null ? job.getHiringManager().getFullName() : null)
                .pipelineTemplateId(job.getPipelineTemplate() != null ? job.getPipelineTemplate().getId() : null)
                .pipelineTemplateName(job.getPipelineTemplate() != null ? job.getPipelineTemplate().getName() : null)
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }
}
