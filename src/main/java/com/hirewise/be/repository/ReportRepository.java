package com.hirewise.be.repository;

import com.hirewise.be.domain.Application;
import com.hirewise.be.repository.projection.ChannelTrafficRow;
import com.hirewise.be.repository.projection.SourceRoiRow;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-only aggregation queries behind the two Reporting dashboards, UC-42
 * (Source ROI) and UC-43 (Pipeline Velocity).
 * <p>
 * Everything here is native PostgreSQL rather than JPQL: the reports need
 * window functions and ordered-set aggregates ({@code LEAD},
 * {@code PERCENTILE_CONT}, {@code FILTER}) that JPQL cannot express. Column
 * aliases are double-quoted so PostgreSQL keeps their camelCase and the
 * interface projections bind.
 * <p>
 * BR-RPT-01: every number is derived from transactional data
 * ({@code applications}, {@code application_stage_history},
 * {@code job_posting_channels}) - nothing is hand-entered or snapshotted.
 * <p>
 * BR-RPT-02: {@code jobIds} comes from
 * {@code ReportScopeResolver#resolveVisibleJobIds} and is intersected with the
 * UI's own department/job filters, so a caller passing an arbitrary
 * {@code departmentId} can never see outside their Access Scope. When the
 * caller holds a SYSTEM scope, {@code allJobs} is {@code true} and
 * {@code jobIds} carries a single unused sentinel value (JPA cannot bind an
 * empty {@code IN} list).
 */
public interface ReportRepository extends Repository<Application, UUID> {

    /**
     * UC-42 normal flow step 3: applications, hires and time-to-hire broken
     * down by acquisition source.
     * <p>
     * The cohort is picked by {@code applied_at}, not by the date a stage
     * changed - so the hire rate reported here and the stage timings of UC-43
     * describe the very same set of applications and can be reconciled against
     * each other. {@code AVG} skips {@code NULL}s, so {@code avgDaysToHire}
     * automatically covers hired applications only.
     *
     * @param fromTs        inclusive lower bound on {@code applied_at}
     * @param toTs          exclusive upper bound on {@code applied_at}
     * @param allJobs       {@code true} to skip the job-id restriction (SYSTEM scope)
     * @param jobIds        job positions the caller may see; never empty
     * @param departmentId  optional UI filter, intersected with the scope above
     * @param jobPositionId optional UI filter, intersected with the scope above
     * @return one row per distinct source, direct traffic keyed by the empty string
     */
    @Query(value = """
            WITH cohort AS (
                SELECT a.id, a.source, a.applied_at
                FROM applications a
                JOIN job_positions jp ON jp.id = a.job_position_id
                WHERE a.applied_at >= :fromTs AND a.applied_at < :toTs
                  AND (:allJobs = TRUE OR a.job_position_id IN (:jobIds))
                  AND (:departmentId IS NULL OR jp.department_id = :departmentId)
                  AND (:jobPositionId IS NULL OR jp.id = :jobPositionId)
            ),
            hired AS (
                SELECT c.id, MIN(h.changed_at) AS hired_at
                FROM cohort c
                JOIN application_stage_history h ON h.application_id = c.id
                JOIN pipeline_stages ps ON ps.pipeline_stage_id = h.to_stage_id
                WHERE ps.stage_type = 'TERMINAL_SUCCESS'
                GROUP BY c.id
            )
            SELECT COALESCE(c.source, '') AS "sourceKey",
                   COUNT(*)               AS "applicationCount",
                   COUNT(hd.id)           AS "hireCount",
                   AVG(CAST(EXTRACT(EPOCH FROM (hd.hired_at - c.applied_at)) / 86400.0
                            AS double precision)) AS "avgDaysToHire"
            FROM cohort c
            LEFT JOIN hired hd ON hd.id = c.id
            GROUP BY COALESCE(c.source, '')
            ORDER BY COUNT(*) DESC
            """, nativeQuery = true)
    List<SourceRoiRow> aggregateSourceRoi(@Param("fromTs") Instant fromTs,
                                          @Param("toTs") Instant toTs,
                                          @Param("allJobs") boolean allJobs,
                                          @Param("jobIds") List<UUID> jobIds,
                                          @Param("departmentId") Long departmentId,
                                          @Param("jobPositionId") UUID jobPositionId);

    /**
     * UC-42: lifetime shares and clicks per channel (recorded by UC-31/UC-32),
     * summed over the Job Positions in the caller's filter.
     * <p>
     * Intentionally has no date predicate - {@code job_posting_channels} stores
     * running totals with no per-event timestamp, so narrowing these by the
     * report's date range is not possible. The UI marks the columns accordingly
     * rather than silently mixing lifetime and windowed numbers.
     *
     * @param allJobs       {@code true} to skip the job-id restriction (SYSTEM scope)
     * @param jobIds        job positions the caller may see; never empty
     * @param departmentId  optional UI filter
     * @param jobPositionId optional UI filter
     * @return one row per channel that has ever been shared for those jobs
     */
    @Query(value = """
            SELECT pc.utm_source        AS "utmSource",
                   SUM(jpc.share_count) AS "shareCount",
                   SUM(jpc.click_count) AS "clickCount"
            FROM job_posting_channels jpc
            JOIN publishing_channels pc ON pc.publishing_channel_id = jpc.publishing_channel_id
            JOIN job_positions jp ON jp.id = jpc.job_position_id
            WHERE (:allJobs = TRUE OR jpc.job_position_id IN (:jobIds))
              AND (:departmentId IS NULL OR jp.department_id = :departmentId)
              AND (:jobPositionId IS NULL OR jp.id = :jobPositionId)
            GROUP BY pc.utm_source
            """, nativeQuery = true)
    List<ChannelTrafficRow> aggregateChannelTraffic(@Param("allJobs") boolean allJobs,
                                                    @Param("jobIds") List<UUID> jobIds,
                                                    @Param("departmentId") Long departmentId,
                                                    @Param("jobPositionId") UUID jobPositionId);
}
