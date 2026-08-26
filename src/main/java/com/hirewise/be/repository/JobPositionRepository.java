package com.hirewise.be.repository;

import com.hirewise.be.domain.EmploymentType;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for {@link JobPosition} entities.
 */
public interface JobPositionRepository extends JpaRepository<JobPosition, UUID> {

    /**
     * UC-16: the public Job Board list, filtered to {@code PUBLISHED} jobs
     * only (BR-APR-03), with optional department/employment type/keyword
     * filters (UC-16 normal flow step 3). Every filter is a no-op when its
     * argument is {@code null}/blank, so the same query backs both the
     * unfiltered list and any combination of filters.
     *
     * @param departmentId   optional department filter
     * @param employmentType optional employment type filter
     * @param keyword        optional case-insensitive substring match on the job title
     * @param pageable       pagination/sort parameters
     * @return a page of matching Published job positions
     */
    @Query("""
            SELECT j FROM JobPosition j
            WHERE j.status = com.hirewise.be.domain.JobStatus.PUBLISHED
              AND (:departmentId IS NULL OR j.department.id = :departmentId)
              AND (:employmentType IS NULL OR j.employmentType = :employmentType)
              AND (:keyword = '' OR LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<JobPosition> searchPublished(@Param("departmentId") Long departmentId,
                                       @Param("employmentType") EmploymentType employmentType,
                                       @Param("keyword") String keyword,
                                       Pageable pageable);

    /**
     * UC-16 step 4 / UC-17 precondition: a single job, but only if it is
     * actually {@code PUBLISHED} - a Draft/Pending/Closed job must 404 on
     * the public board exactly like a non-existent id (BR-APR-03), rather
     * than leaking its existence/content to an anonymous candidate.
     *
     * @param id     job position id
     * @param status must be {@link JobStatus#PUBLISHED}
     * @return the job, if it exists and is Published
     */
    Optional<JobPosition> findByIdAndStatus(UUID id, JobStatus status);

    /**
     * Powers the public Job Board's department filter dropdown (UC-16 REF
     * 2) - only departments that currently have at least one Published job
     * are worth offering, so the list stays short and every option
     * actually returns results.
     *
     * @return ids of departments with at least one Published job
     */
    @Query("""
            SELECT DISTINCT j.department.id FROM JobPosition j
            WHERE j.status = com.hirewise.be.domain.JobStatus.PUBLISHED AND j.department IS NOT NULL
            """)
    java.util.List<Long> findDistinctDepartmentIdsWithPublishedJobs();

    /**
     * UC-14: returns job positions whose owning department is within the calling
     * Hiring Manager's access scope (BR-APR-01, BR-RBAC-01), with optional status filter.
     * If status is null, returns all approval-relevant jobs (PENDING_APPROVAL, APPROVED, REJECTED, etc.).
     *
     * @param allowedDepartmentIds department ids the manager is scoped to
     * @param status               optional status filter (null = all approval statuses)
     * @param pageable             pagination and sort parameters
     * @return page of matching jobs within the manager's scope
     */
    @Query("""
            SELECT DISTINCT j FROM JobPosition j
            LEFT JOIN FETCH j.department
            LEFT JOIN FETCH j.recruiter
            LEFT JOIN FETCH j.pipelineTemplate
            WHERE (:status IS NULL OR j.status = :status)
              AND j.department.id IN :allowedDepartmentIds
            """)
    Page<JobPosition> findApprovalJobsInDepartments(
            @Param("allowedDepartmentIds") java.util.List<Long> allowedDepartmentIds,
            @Param("status") JobStatus status,
            Pageable pageable);

    /**
     * UC-14 (SYSTEM scope variant): Hiring Managers with SYSTEM-wide access scope.
     *
     * @param status   optional status filter
     * @param pageable pagination and sort parameters
     * @return page of all matching jobs
     */
    @Query("""
            SELECT DISTINCT j FROM JobPosition j
            LEFT JOIN FETCH j.department
            LEFT JOIN FETCH j.recruiter
            LEFT JOIN FETCH j.pipelineTemplate
            WHERE (:status IS NULL OR j.status = :status)
            """)
    Page<JobPosition> findAllApprovalJobs(
            @Param("status") JobStatus status,
            Pageable pageable);
}

