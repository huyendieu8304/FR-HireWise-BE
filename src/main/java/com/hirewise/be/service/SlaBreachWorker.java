package com.hirewise.be.service;

import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.User;
import com.hirewise.be.event.OutboxEventPublisher;
import com.hirewise.be.event.OutboxEventType;
import com.hirewise.be.repository.ApplicationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * US-MGR-05 (UC-41, SLA Monitoring): sweeps for Applications past their
 * Stage's SLA that have not yet triggered an alert email, and sends one EM-13
 * per (Job, Stage) group to that Job's Hiring Manager - grouped, not one
 * email per candidate, so a Stage stuck with 5 overdue candidates produces 1
 * email listing all 5 rather than flooding the inbox.
 * <p>
 * Idempotent per stage-dwell via {@link Application#getSlaAlertSentAt()} -
 * every stage-transition call site resets it to {@code null} (see that
 * field's Javadoc), so a still-breaching candidate is only ever emailed
 * once per dwell in a given Stage, not on every poll.
 * <p>
 * Same {@code @Scheduled} shape as {@code OfferExpiryWorker}/{@code ScorecardLockWorker}.
 */
@Slf4j
@Component
public class SlaBreachWorker {

    private final ApplicationRepository applicationRepository;
    private final SlaMonitoringService slaMonitoringService;
    private final OutboxEventPublisher outboxEventPublisher;
    private final Clock clock;
    private final String dashboardLink;

    public SlaBreachWorker(ApplicationRepository applicationRepository,
                            SlaMonitoringService slaMonitoringService,
                            OutboxEventPublisher outboxEventPublisher,
                            Clock clock,
                            @Value("${app.share.frontend-base-url}") String frontendBaseUrl) {
        this.applicationRepository = applicationRepository;
        this.slaMonitoringService = slaMonitoringService;
        this.outboxEventPublisher = outboxEventPublisher;
        this.clock = clock;
        this.dashboardLink = frontendBaseUrl.replaceAll("/$", "") + "/dashboard";
    }

    /** Groups a breach by the (Job, Stage) pair its alert email is sent about. */
    private record GroupKey(java.util.UUID jobId, Long stageId) {
    }

    @Scheduled(fixedDelayString = "${app.sla.breach-poll-interval-ms:300000}")
    @Transactional
    public void sendBreachAlerts() {
        List<SlaMonitoringService.Breach> unalerted = slaMonitoringService.findAllCurrentBreaches().stream()
                .filter(b -> b.application().getSlaAlertSentAt() == null)
                .toList();
        if (unalerted.isEmpty()) {
            return;
        }

        Map<GroupKey, List<SlaMonitoringService.Breach>> grouped = unalerted.stream()
                .collect(Collectors.groupingBy(b ->
                        new GroupKey(b.application().getJobPosition().getId(), b.stage().getId())));

        for (List<SlaMonitoringService.Breach> group : grouped.values()) {
            sendGroupAlert(group);
        }

        Instant now = Instant.now(clock);
        List<Application> breachingApplications = unalerted.stream()
                .map(SlaMonitoringService.Breach::application)
                .toList();
        breachingApplications.forEach(a -> a.setSlaAlertSentAt(now));
        applicationRepository.saveAll(breachingApplications);

        log.info("SLA breach sweep: alerted {} application(s) across {} (Job, Stage) group(s)",
                unalerted.size(), grouped.size());
    }

    /**
     * Enqueues 1 EM-13 email for 1 (Job, Stage) group. A Job with no Hiring
     * Manager assigned has nobody to alert - logged and skipped rather than
     * failing the whole sweep over 1 misconfigured Job.
     */
    private void sendGroupAlert(List<SlaMonitoringService.Breach> group) {
        JobPosition job = group.get(0).application().getJobPosition();
        User manager = job.getHiringManager();
        if (manager == null) {
            log.warn("Job {} co Application vuot SLA nhung chua co Hiring Manager de canh bao", job.getId());
            return;
        }

        String breachList = group.stream()
                .map(b -> "- " + b.application().getCandidate().getFullName()
                        + " (qua " + b.hoursOverdue() + " gio)")
                .collect(Collectors.joining("<br/>"));

        Map<String, Object> payload = new HashMap<>();
        payload.put("email", manager.getEmail());
        payload.put("managerName", manager.getFullName());
        payload.put("n", group.size());
        payload.put("stageName", group.get(0).stage().getName());
        payload.put("jobTitle", job.getTitle());
        payload.put("breachList", breachList);
        payload.put("dashboardLink", dashboardLink);
        outboxEventPublisher.publish(OutboxEventType.SLA_BREACH_ALERT_EMAIL, payload);
    }
}
