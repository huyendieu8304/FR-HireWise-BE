package com.hirewise.be.mapper;

import com.hirewise.be.domain.Department;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.dto.response.JobBoardDetailResponseDto;
import com.hirewise.be.dto.response.JobBoardSummaryResponseDto;

/**
 * Converts {@link JobPosition} entities into the public Job Board's
 * response DTOs (UC-16). Never exposes anything beyond what an anonymous
 * candidate is meant to see - no recruiter/hiring manager, no internal
 * status/approval trail.
 */
public final class JobBoardMapper {

    private JobBoardMapper() {
    }

    public static JobBoardSummaryResponseDto toSummaryDto(JobPosition entity) {
        return JobBoardSummaryResponseDto.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .departmentName(departmentName(entity))
                .employmentType(entity.getEmploymentType())
                .location(entity.getLocation())
                .salaryMin(entity.getSalaryMin())
                .salaryMax(entity.getSalaryMax())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    public static JobBoardDetailResponseDto toDetailDto(JobPosition entity) {
        return JobBoardDetailResponseDto.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .departmentName(departmentName(entity))
                .employmentType(entity.getEmploymentType())
                .location(entity.getLocation())
                .salaryMin(entity.getSalaryMin())
                .salaryMax(entity.getSalaryMax())
                .openings(entity.getOpenings())
                .applicationDeadline(entity.getApplicationDeadline())
                .description(entity.getDescription())
                .requirements(entity.getRequirements())
                .benefits(entity.getBenefits())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private static String departmentName(JobPosition entity) {
        Department department = entity.getDepartment();
        return department == null ? null : department.getName();
    }
}
