package com.hirewise.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * US-MGR-05 (UC-41, SLA Monitoring): one Application currently past its
 * Stage's SLA threshold, for the Dashboard's "Vi phạm SLA" widget. Live -
 * reflects every CURRENT breach regardless of whether its alert email has
 * already fired (see {@code ApplicationRepository#findSlaBreachCandidates}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlaAlertResponseDto {
    private UUID applicationId;
    private String candidateName;
    private String jobTitle;
    private String stageName;
    /** How many whole hours past {@code stage.slaHours} the Application has been sitting here. */
    private long hoursOverdue;
}
