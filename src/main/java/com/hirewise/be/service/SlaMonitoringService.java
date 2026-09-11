package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ReportScopeResolver;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.PipelineStage;
import com.hirewise.be.dto.response.SlaAlertResponseDto;
import com.hirewise.be.repository.ApplicationRepository;
import com.hirewise.be.security.CurrentUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Module M19 - SLA Monitoring: the shared "which Applications are CURRENTLY
 * breaching their Stage's SLA" computation behind both US-MGR-05 (UC-41)'s
 * read-only alert list and {@link SlaBreachWorker}'s outbound emails.
 * <p>
 * {@code sla_hours} lives on {@link PipelineStage}, not per (Job, Stage) like
 * the Scorecard redesign (US-MGR-03) - a Pipeline Template's Stage timing
 * expectation is shared by every Job that uses it, there is no per-Job
 * override here.
 */
@Service
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SlaMonitoringService {

    ApplicationRepository applicationRepository;
    ReportScopeResolver reportScopeResolver;
    AccessControlService accessControlService;
    Clock clock;

    /**
     * One Application currently past its Stage's SLA threshold.
     *
     * @param application   the breaching Application (candidate/currentStage/jobPosition eagerly fetched)
     * @param stage         same as {@code application.getCurrentStage()}, named for readability at call sites
     * @param hoursOverdue  whole hours elapsed since {@code lastStageChangedAt}, always {@code >= stage.slaHours}
     */
    public record Breach(Application application, PipelineStage stage, long hoursOverdue) {
    }

    /**
     * Every Application currently breaching its Stage's SLA, system-wide -
     * unscoped by caller, since {@link SlaBreachWorker} has no "current user"
     * and the read path ({@link #getSlaAlerts}) applies its own scope
     * afterward.
     *
     * @return current breaches, oldest stage-dwell first (worst first)
     */
    List<Breach> findAllCurrentBreaches() {
        Instant now = Instant.now(clock);
        return applicationRepository.findSlaBreachCandidates().stream()
                .map(a -> new Breach(a, a.getCurrentStage(),
                        Duration.between(a.getLastStageChangedAt(), now).toHours()))
                .filter(b -> b.hoursOverdue() >= b.stage().getSlaHours())
                .toList();
    }

    /**
     * US-MGR-05 (UC-41) normal flow: the Dashboard's "Vi phạm SLA" widget.
     * Scoped the same way as the UC-42/43 Reports (BR-RPT-02 via
     * {@link ReportScopeResolver}) - a Recruiter/Hiring Manager only sees
     * breaches for Jobs within their Access Scope.
     *
     * @param currentUser authenticated caller, must hold {@code SLA_VIEW_ALERT}
     * @return current breaches the caller may see, worst (most overdue) first
     */
    public List<SlaAlertResponseDto> getSlaAlerts(CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.SLA_VIEW_ALERT, ResourceContext.none());

        List<UUID> visibleJobIds = reportScopeResolver.resolveVisibleJobIds(currentUser);
        boolean allJobs = visibleJobIds == null;

        return findAllCurrentBreaches().stream()
                .filter(b -> allJobs || visibleJobIds.contains(b.application().getJobPosition().getId()))
                .sorted(Comparator.comparingLong(Breach::hoursOverdue).reversed())
                .map(SlaMonitoringService::toDto)
                .toList();
    }

    private static SlaAlertResponseDto toDto(Breach breach) {
        Application application = breach.application();
        return SlaAlertResponseDto.builder()
                .applicationId(application.getId())
                .candidateName(application.getCandidate().getFullName())
                .jobTitle(application.getJobPosition().getTitle())
                .stageName(breach.stage().getName())
                .hoursOverdue(breach.hoursOverdue())
                .build();
    }
}
