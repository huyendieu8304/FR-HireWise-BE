package com.hirewise.be.repository;

import com.hirewise.be.domain.Application;
import com.hirewise.be.repository.projection.ChannelTrafficRow;
import com.hirewise.be.repository.projection.SourceRoiRow;
import com.hirewise.be.repository.projection.StageVelocityRow;
import com.hirewise.be.repository.projection.TimeToHireRow;
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
 * UI department/job filters, so a caller passing an arbitrary
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
     * summed over the Job Positions in the caller filter.
     * <p>
     * Intentionally has no date predicate - {@code job_posting_channels} stores
     * running totals with no per-event timestamp, so narrowing these by the
     * report date range is not possible. The UI marks the columns accordingly
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

    /**
     * UC-43 normal flow steps 3-4: how long applications sit in each Stage.
     * <p>
     * {@code application_stage_history} is an append-only log with a single
     * {@code changed_at} - there is no {@code entered_at}/{@code exited_at}
     * pair to subtract. A visit to a stage is therefore reconstructed from two
     * consecutive events of the same application: the row that moved it in
     * opens the visit, the next row closes it. Hence {@code LEAD}, and hence
     * native SQL.
     * <p>
     * The last event of an application has no successor, so its duration is
     * {@code NULL}: that visit is still running. {@code AVG} and
     * {@code PERCENTILE_CONT} skip those rows, keeping unfinished waits out of
     * the averages, while {@code waitingCount}/{@code maxWaitingDays} report
     * them separately - without that pair, a stage where everything is stuck
     * and nothing moves would show a flatteringly low average.
     * <p>
     * Terminal stages are excluded: an application that reaches one never
     * leaves, so every figure for them would be an artefact.
     * <p>
     * Stages are grouped by {@code code} rather than by id, because a filter
     * spanning several Jobs spans several Pipeline Templates, each with its own
     * {@code pipeline_stages} rows. The display name and type are taken from
     * the earliest-positioned stage carrying that code, and the chart is
     * ordered by that same position.
     *
     * @param fromTs        inclusive lower bound on {@code applied_at}
     * @param toTs          exclusive upper bound on {@code applied_at}
     * @param nowTs         the current instant, for measuring in-progress waits;
     *                      passed in from the injected {@code Clock} so tests
     *                      stay deterministic
     * @param allJobs       {@code true} to skip the job-id restriction (SYSTEM scope)
     * @param jobIds        job positions the caller may see; never empty
     * @param departmentId  optional UI filter, intersected with the scope above
     * @param jobPositionId optional UI filter, intersected with the scope above
     * @return one row per non-terminal stage, in pipeline order
     */
    @Query(value = """
            WITH cohort AS (
                SELECT a.id
                FROM applications a
                JOIN job_positions jp ON jp.id = a.job_position_id
                WHERE a.applied_at >= :fromTs AND a.applied_at < :toTs
                  AND (:allJobs = TRUE OR a.job_position_id IN (:jobIds))
                  AND (:departmentId IS NULL OR jp.department_id = :departmentId)
                  AND (:jobPositionId IS NULL OR jp.id = :jobPositionId)
            ),
            events AS (
                SELECT h.to_stage_id,
                       h.changed_at AS entered_at,
                       LEAD(h.changed_at) OVER (
                           PARTITION BY h.application_id
                           ORDER BY h.changed_at, h.application_stage_history_id) AS exited_at,
                       LEAD(h.to_stage_id) OVER (
                           PARTITION BY h.application_id
                           ORDER BY h.changed_at, h.application_stage_history_id) AS next_stage_id
                FROM application_stage_history h
                JOIN cohort c ON c.id = h.application_id
            ),
            spans AS (
                SELECT e.to_stage_id,
                       e.entered_at,
                       e.exited_at,
                       CAST(EXTRACT(EPOCH FROM (e.exited_at - e.entered_at)) / 86400.0
                            AS double precision) AS days,
                       nx.stage_type AS next_stage_type
                FROM events e
                LEFT JOIN pipeline_stages nx ON nx.pipeline_stage_id = e.next_stage_id
            )
            SELECT ps.code                                            AS "stageCode",
                   (array_agg(ps.name ORDER BY ps.position))[1]       AS "stageName",
                   (array_agg(ps.stage_type ORDER BY ps.position))[1] AS "stageType",
                   MIN(ps.position)                                   AS "stageOrder",
                   MIN(ps.sla_hours)                                  AS "slaHours",
                   COUNT(s.days)                                      AS "completedCount",
                   AVG(s.days)                                        AS "avgDays",
                   PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY s.days) AS "medianDays",
                   PERCENTILE_CONT(0.9) WITHIN GROUP (ORDER BY s.days) AS "p90Days",
                   COUNT(*)                                           AS "enteredCount",
                   COUNT(*) FILTER (WHERE s.next_stage_type IS NOT NULL
                                      AND s.next_stage_type <> 'TERMINAL_REJECTED')
                                                                      AS "advancedCount",
                   COUNT(*) FILTER (WHERE s.next_stage_type = 'TERMINAL_REJECTED')
                                                                      AS "rejectedCount",
                   COUNT(*) FILTER (WHERE s.exited_at IS NULL)        AS "waitingCount",
                   MAX(CAST(EXTRACT(EPOCH FROM (CAST(:nowTs AS timestamptz) - s.entered_at))
                            / 86400.0 AS double precision))
                       FILTER (WHERE s.exited_at IS NULL)             AS "maxWaitingDays"
            FROM spans s
            JOIN pipeline_stages ps ON ps.pipeline_stage_id = s.to_stage_id
            WHERE ps.is_terminal = FALSE
            GROUP BY ps.code
            ORDER BY MIN(ps.position), ps.code
            """, nativeQuery = true)
    List<StageVelocityRow> aggregateStageVelocity(@Param("fromTs") Instant fromTs,
                                                  @Param("toTs") Instant toTs,
                                                  @Param("nowTs") Instant nowTs,
                                                  @Param("allJobs") boolean allJobs,
                                                  @Param("jobIds") List<UUID> jobIds,
                                                  @Param("departmentId") Long departmentId,
                                                  @Param("jobPositionId") UUID jobPositionId);

    /**
     * UC-43: end-to-end Time-to-Hire over the same cohort as
     * {@link #aggregateStageVelocity}, so the headline number and the per-stage
     * bars below it always describe the same applications.
     *
     * @param fromTs        inclusive lower bound on {@code applied_at}
     * @param toTs          exclusive upper bound on {@code applied_at}
     * @param allJobs       {@code true} to skip the job-id restriction (SYSTEM scope)
     * @param jobIds        job positions the caller may see; never empty
     * @param departmentId  optional UI filter
     * @param jobPositionId optional UI filter
     * @return exactly one row; zero hires yields a zero count and a null average
     */
    @Query(value = """
            WITH cohort AS (
                SELECT a.id, a.applied_at
                FROM applications a
                JOIN job_positions jp ON jp.id = a.job_position_id
                WHERE a.applied_at >= :fromTs AND a.applied_at < :toTs
                  AND (:allJobs = TRUE OR a.job_position_id IN (:jobIds))
                  AND (:departmentId IS NULL OR jp.department_id = :departmentId)
                  AND (:jobPositionId IS NULL OR jp.id = :jobPositionId)
            ),
            hired AS (
                SELECT c.id, c.applied_at, MIN(h.changed_at) AS hired_at
                FROM cohort c
                JOIN application_stage_history h ON h.application_id = c.id
                JOIN pipeline_stages ps ON ps.pipeline_stage_id = h.to_stage_id
                WHERE ps.stage_type = 'TERMINAL_SUCCESS'
                GROUP BY c.id, c.applied_at
            )
            SELECT COUNT(*) AS "hiredCount",
                   AVG(CAST(EXTRACT(EPOCH FROM (hd.hired_at - hd.applied_at)) / 86400.0
                            AS double precision)) AS "avgDaysToHire"
            FROM hired hd
            """, nativeQuery = true)
    TimeToHireRow aggregateTimeToHire(@Param("fromTs") Instant fromTs,
                                      @Param("toTs") Instant toTs,
                                      @Param("allJobs") boolean allJobs,
                                      @Param("jobIds") List<UUID> jobIds,
                                      @Param("departmentId") Long departmentId,
                                      @Param("jobPositionId") UUID jobPositionId);
}
