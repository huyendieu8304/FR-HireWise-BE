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
     * UC-14 normal flow step 2: returns all PENDING_APPROVAL job positions
     * whose owning department is within the calling Hiring Manager's access
     * scope (BR-APR-01, BR-RBAC-01).
     *
     * <p>Uses JOIN FETCH on {@code department} and {@code recruiter} to load
     * the related entities in the same query and prevent N+1 selects when the
     * service maps each row to a {@code PendingApprovalJobSummaryResponseDto}.
     *
     * @param allowedDepartmentIds department ids the manager is scoped to
     *                             (already expanded with sub-departments by the
     *                             service before calling this method)
     * @param pageable             pagination and sort parameters
     * @return page of PENDING_APPROVAL jobs within the manager's scope,
     *         ordered by submission time (createdAt desc)
     */
    @Query("""
            SELECT DISTINCT j FROM JobPosition j
            LEFT JOIN FETCH j.department
            LEFT JOIN FETCH j.recruiter
            WHERE j.status = com.hirewise.be.domain.JobStatus.PENDING_APPROVAL
              AND j.department.id IN :allowedDepartmentIds
            """)
    Page<JobPosition> findPendingApprovalInDepartments(
            @Param("allowedDepartmentIds") java.util.List<Long> allowedDepartmentIds,
            Pageable pageable);

    /**
     * UC-14 (SYSTEM scope variant): Hiring Managers with a SYSTEM-wide access
     * scope (e.g. HR Admin acting as approver) see every PENDING_APPROVAL job,
     * regardless of department.
     *
     * @param pageable pagination and sort parameters
     * @return page of all PENDING_APPROVAL jobs
     */
    @Query("""
            SELECT DISTINCT j FROM JobPosition j
            LEFT JOIN FETCH j.department
            LEFT JOIN FETCH j.recruiter
            WHERE j.status = com.hirewise.be.domain.JobStatus.PENDING_APPROVAL
            """)
    Page<JobPosition> findAllPendingApproval(Pageable pageable);
}
