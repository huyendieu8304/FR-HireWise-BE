package com.hirewise.be.dto.response;

import com.hirewise.be.domain.ApplicationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * One Kanban card (UC-22) - a Candidate's Application shown inside a Stage
 * column, with just enough candidate contact info to identify them without
 * a second call to the Applicant Card detail screen (UC-20).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationCardResponseDto {
    private UUID applicationId;
    private UUID candidateId;
    private String candidateName;
    private String candidateEmail;
    private String candidatePhone;
    private ApplicationStatus status;
    private Instant appliedAt;
    private Instant lastStageChangedAt;
}
