package com.hirewise.be.controller;

import com.hirewise.be.dto.response.SlaAlertResponseDto;
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.security.CurrentUserPrincipal;
import com.hirewise.be.service.SlaMonitoringService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Module M19 - SLA Monitoring. US-MGR-05 (UC-41)'s read-only counterpart to
 * {@code SlaBreachWorker}'s emails: the same "current breaches" computation,
 * for the Dashboard's "Vi phạm SLA" widget.
 * <p>
 * No {@code @RequiresOwnership}/{@code ResourceContext} here, same reasoning
 * as {@code ReportController}: this spans many Jobs at once, so authorisation
 * is the {@code SLA_VIEW_ALERT} permission check inside
 * {@link SlaMonitoringService}, and the data is narrowed by the caller's
 * Access Scope (BR-RPT-02, reused via {@code ReportScopeResolver}).
 */
@RestController
@RequestMapping("/api/sla-alerts")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class SlaAlertController {

    SlaMonitoringService slaMonitoringService;

    /**
     * US-MGR-05 (UC-41): every Application currently past its Stage's SLA
     * that the caller may see, worst (most overdue) first.
     *
     * @param currentUser authenticated caller, must hold {@code SLA_VIEW_ALERT}
     * @return current breaches in the caller's Access Scope
     */
    @GetMapping
    public ResponseEntity<List<SlaAlertResponseDto>> listAlerts(@CurrentUserPrincipal CurrentUser currentUser) {
        return ResponseEntity.ok(slaMonitoringService.getSlaAlerts(currentUser));
    }
}
