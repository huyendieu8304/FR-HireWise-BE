package com.hirewise.be.service;

import com.hirewise.be.domain.EmploymentType;
import com.hirewise.be.domain.JobStatus;
import com.hirewise.be.dto.PagedResponseDto;
import com.hirewise.be.dto.response.DepartmentResponseDto;
import com.hirewise.be.dto.response.JobBoardDetailResponseDto;
import com.hirewise.be.dto.response.JobBoardFilterOptionsResponseDto;
import com.hirewise.be.dto.response.JobBoardSummaryResponseDto;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.mapper.DepartmentMapper;
import com.hirewise.be.mapper.JobBoardMapper;
import com.hirewise.be.repository.DepartmentRepository;
import com.hirewise.be.repository.JobPositionRepository;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * UC-16: the public Job Board (list, filters, single-job detail). Every
 * method here is read-only and safe to expose with no authentication - see
 * {@code /api/public/jobs/**} in {@code SecurityConfig}.
 */
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class PublicJobBoardService {

    JobPositionRepository jobPositionRepository;
    DepartmentRepository departmentRepository;

    /**
     * UC-16 step 2-3: the Job Board card list, optionally filtered.
     *
     * @param departmentId   optional department filter
     * @param employmentType optional employment type filter
     * @param keyword        optional free-text filter, matched against the job title
     * @param pageable       pagination/sort
     * @return a page of Published jobs; empty (not an error) when none match - EX-01
     */
    public PagedResponseDto<JobBoardSummaryResponseDto> list(Long departmentId, EmploymentType employmentType,
                                                              String keyword, Pageable pageable) {
        String normalizedKeyword = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        Page<JobPosition> page = jobPositionRepository.searchPublished(departmentId, employmentType, normalizedKeyword, pageable);
        List<JobBoardSummaryResponseDto> content = page.getContent().stream()
                .map(JobBoardMapper::toSummaryDto)
                .toList();
        return PagedResponseDto.from(page, content);
    }

    /**
     * UC-16 step 4: full JD for one Published job.
     *
     * @param jobId job position id
     * @return the job's public detail view
     * @throws ResourceNotFoundException if no such job exists, or it exists but isn't Published
     *                                    (BR-APR-03 - treated identically to "not found" for an
     *                                    anonymous candidate, see {@code JobPositionRepository#findByIdAndStatus})
     */
    public JobBoardDetailResponseDto getPublishedDetail(UUID jobId) {
        JobPosition job = jobPositionRepository.findByIdAndStatus(jobId, JobStatus.PUBLISHED)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.JOB_POSITION_NOT_FOUND, jobId));
        return JobBoardMapper.toDetailDto(job);
    }

    /**
     * UC-16 REF 2: options for the filter dropdowns (departments that
     * currently have a Published job, plus the fixed set of employment types).
     *
     * @return the filter options
     */
    public JobBoardFilterOptionsResponseDto filterOptions() {
        List<Long> departmentIds = jobPositionRepository.findDistinctDepartmentIdsWithPublishedJobs();
        List<DepartmentResponseDto> departments = departmentRepository.findAllById(departmentIds).stream()
                .map(DepartmentMapper::toResponseDto)
                .sorted((a, b) -> a.getName().compareToIgnoreCase(b.getName()))
                .toList();
        return JobBoardFilterOptionsResponseDto.builder()
                .departments(departments)
                .employmentTypes(List.of(EmploymentType.values()))
                .build();
    }
}
