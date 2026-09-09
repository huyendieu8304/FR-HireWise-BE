package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ReportScopeResolver;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.PublishingChannel;
import com.hirewise.be.dto.response.SourceRoiReportResponseDto;
import com.hirewise.be.mapper.ReportMapper;
import com.hirewise.be.repository.PublishingChannelRepository;
import com.hirewise.be.repository.ReportRepository;
import com.hirewise.be.repository.projection.ChannelTrafficRow;
import com.hirewise.be.repository.projection.SourceRoiRow;
import com.hirewise.be.security.CurrentUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Module M20 - Reporting and Analytics: the read models behind UC-42 (Source
 * ROI) and UC-43 (Pipeline Velocity).
 *
 * <p>Four decisions shape every number this service returns, and none of them
 * is visible from the SQL alone:</p>
 * <ol>
 *   <li><b>The cohort is chosen by {@code applied_at}</b>, not by the date a
 *       stage changed. The date filter picks a <i>set of applications</i>, then
 *       their whole lifecycle is measured. Both dashboards therefore describe
 *       the same applications and reconcile against each other.</li>
 *   <li><b>An empty result is a success, not an error.</b> EX-01/ME-37 is a
 *       message the UI prints over an empty chart; throwing would fire the
 *       automatic error toast on the front end and make the filter look broken.</li>
 *   <li><b>A ratio with a zero denominator is {@code null}, never {@code 0}.</b>
 *       "No data" and "zero percent" lead to opposite decisions, so the UI has
 *       to be able to tell them apart.</li>
 *   <li><b>Rankings ignore samples below {@code MIN_SAMPLE_SIZE}.</b> Without
 *       that floor a source with one application and one hire would top every
 *       league table at 100%.</li>
 * </ol>
 *
 * <p>BR-RPT-02 is enforced by {@link ReportScopeResolver}, which turns the
 * Access Scope of the caller into a job-id set that every query is restricted
 * to. The permission check itself passes {@link ResourceContext#none()} because
 * a report spans many departments and jobs at once - RBAC layer 3 takes a
 * single department/job and cannot express that, so layer 2 authorises the
 * action and the resolver narrows the data.</p>
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class ReportService {

    /**
     * Minimum applications (UC-42) or completed transitions (UC-43) before a
     * source or stage may be singled out as best or worst.
     */
    private static final int MIN_SAMPLE_SIZE = 5;

    /** How far back the dashboards look when the user has not picked a range. */
    private static final int DEFAULT_RANGE_DAYS = 90;

    /**
     * Stand-in bound for {@code IN (:jobIds)} when the caller has SYSTEM scope
     * and the predicate is switched off by {@code allJobs}. JPA cannot bind an
     * empty list, and this value can never match a real job.
     */
    private static final List<UUID> ALL_JOBS_SENTINEL = List.of(new UUID(0L, 0L));

    AccessControlService accessControlService;
    ReportScopeResolver reportScopeResolver;
    ReportRepository reportRepository;
    PublishingChannelRepository publishingChannelRepository;
    Clock clock;

    /**
     * UC-42 normal flow steps 2-3: the Source Effectiveness dashboard.
     *
     * @param currentUser   authenticated caller, must hold {@code REPORT_VIEW}
     * @param fromDate      inclusive first day of the range; defaults to 90 days back
     * @param toDate        inclusive last day of the range; defaults to today
     * @param departmentId  optional department filter, intersected with the caller scope
     * @param jobPositionId optional Job filter, intersected with the caller scope
     * @return per-source volumes, hire rates and traffic; empty rows when the
     *         filter matches nothing (EX-01, ME-37)
     */
    @Transactional(readOnly = true)
    public SourceRoiReportResponseDto getSourceRoiReport(CurrentUser currentUser,
                                                         LocalDate fromDate,
                                                         LocalDate toDate,
                                                         Long departmentId,
                                                         UUID jobPositionId) {

        accessControlService.checkAccess(currentUser, PermissionCodes.REPORT_VIEW, ResourceContext.none());

        ReportFilter filter = resolveFilter(currentUser, fromDate, toDate, departmentId, jobPositionId);
        if (filter.seesNothing()) {
            return ReportMapper.emptySourceRoi(filter.fromDate(), filter.toDate());
        }

        List<SourceRoiRow> sourceRows = reportRepository.aggregateSourceRoi(
                filter.fromTs(), filter.toTs(), filter.allJobs(), filter.jobIds(),
                filter.departmentId(), filter.jobPositionId());

        List<ChannelTrafficRow> trafficRows = reportRepository.aggregateChannelTraffic(
                filter.allJobs(), filter.jobIds(), filter.departmentId(), filter.jobPositionId());

        Map<String, PublishingChannel> channelsByUtmSource = publishingChannelRepository
                .findAllByOrderByDisplayOrderAsc().stream()
                .collect(Collectors.toMap(PublishingChannel::getUtmSource, Function.identity(),
                        (first, duplicate) -> first));

        Map<String, ChannelTrafficRow> trafficByUtmSource = trafficRows.stream()
                .collect(Collectors.toMap(ChannelTrafficRow::getUtmSource, Function.identity(),
                        (first, duplicate) -> first));

        return ReportMapper.toSourceRoiReport(sourceRows, trafficByUtmSource, channelsByUtmSource,
                filter.fromDate(), filter.toDate(), MIN_SAMPLE_SIZE);
    }

    /**
     * Turns the raw request parameters into the exact bounds and job-id set the
     * queries take.
     *
     * <p>The range is half-open, {@code [fromDate 00:00, toDate+1d 00:00)} in
     * UTC - the whole build runs on {@code -Duser.timezone=UTC}, so an
     * application submitted at 23:30 on the last selected day still counts.</p>
     *
     * @param currentUser   the caller whose Access Scope narrows the job set
     * @param fromDate      requested first day, or {@code null} for the default window
     * @param toDate        requested last day, or {@code null} for today
     * @param departmentId  optional UI filter
     * @param jobPositionId optional UI filter
     * @return the resolved filter; {@link ReportFilter#seesNothing()} when the
     *         caller has no job in scope at all
     */
    private ReportFilter resolveFilter(CurrentUser currentUser,
                                       LocalDate fromDate,
                                       LocalDate toDate,
                                       Long departmentId,
                                       UUID jobPositionId) {

        LocalDate today = LocalDate.ofInstant(Instant.now(clock), ZoneOffset.UTC);
        LocalDate resolvedTo = toDate != null ? toDate : today;
        LocalDate resolvedFrom = fromDate != null ? fromDate : resolvedTo.minusDays(DEFAULT_RANGE_DAYS);
        if (resolvedFrom.isAfter(resolvedTo)) {
            // A backwards range is a slip in the UI, not something worth a 400 -
            // swapping the ends shows the user what they meant to ask for.
            LocalDate swap = resolvedFrom;
            resolvedFrom = resolvedTo;
            resolvedTo = swap;
        }

        List<UUID> visibleJobIds = reportScopeResolver.resolveVisibleJobIds(currentUser);
        boolean allJobs = visibleJobIds == null;
        boolean seesNothing = !allJobs && visibleJobIds.isEmpty();

        return new ReportFilter(
                resolvedFrom,
                resolvedTo,
                resolvedFrom.atStartOfDay(ZoneOffset.UTC).toInstant(),
                resolvedTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant(),
                allJobs,
                allJobs ? ALL_JOBS_SENTINEL : visibleJobIds,
                departmentId,
                jobPositionId,
                seesNothing);
    }

    /**
     * The report request after defaults, scope and time zone have been applied.
     * Shared by both dashboards so their cohorts are guaranteed identical.
     *
     * @param fromDate      inclusive first day, as echoed back to the UI
     * @param toDate        inclusive last day, as echoed back to the UI
     * @param fromTs        inclusive lower bound on {@code applied_at}
     * @param toTs          exclusive upper bound on {@code applied_at}
     * @param allJobs       {@code true} when the caller has SYSTEM scope
     * @param jobIds        job ids the queries are restricted to; never empty
     * @param departmentId  optional UI filter
     * @param jobPositionId optional UI filter
     * @param seesNothing   {@code true} when the caller has no job in scope
     */
    private record ReportFilter(LocalDate fromDate,
                                LocalDate toDate,
                                Instant fromTs,
                                Instant toTs,
                                boolean allJobs,
                                List<UUID> jobIds,
                                Long departmentId,
                                UUID jobPositionId,
                                boolean seesNothing) {
    }
}
