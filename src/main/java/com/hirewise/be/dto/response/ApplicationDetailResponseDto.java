package com.hirewise.be.dto.response;

import com.hirewise.be.domain.ApplicationStatus;
import com.hirewise.be.domain.CandidateStatus;
import com.hirewise.be.domain.StageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * UC-20 main flow: the Applicant Card - full detail of one Candidate's
 * Application to one Job, for Recruiter/Hiring Manager (and, since both
 * already hold {@code APPLICATION_VIEW}, Interviewer). Combines what the
 * Kanban card ({@link ApplicationCardResponseDto}) already shows with the
 * candidate's full contact/status info, attached files, the full stage
 * timeline, and - once rejected (UC-29) - the rejection record.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationDetailResponseDto {
    private UUID applicationId;

    private UUID candidateId;
    private String candidateName;
    private String candidateEmail;
    private String candidatePhone;
    private CandidateStatus candidateStatus;

    private UUID jobId;
    private String jobTitle;

    private Long currentStageId;
    private String currentStageName;
    private StageType currentStageType;
    private boolean currentStageTerminal;

    private ApplicationStatus status;
    private Instant appliedAt;
    private Instant lastStageChangedAt;

    private List<ApplicationFileResponseDto> files;
    private List<ApplicationStageHistoryResponseDto> stageHistory;

    /** {@code null} unless this Application was rejected (UC-29). */
    private ApplicationRejectionResponseDto rejection;
}
