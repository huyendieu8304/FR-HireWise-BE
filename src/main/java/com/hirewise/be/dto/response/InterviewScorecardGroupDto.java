package com.hirewise.be.dto.response;

import com.hirewise.be.domain.InterviewMode;
import com.hirewise.be.domain.InterviewStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** 1 Interview's group of Scorecard submissions, for the Applicant Card [Scorecard] tab. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterviewScorecardGroupDto {
    private UUID interviewId;
    /** Tên Stage phỏng vấn (UC-28 req 5) - vd "Technical Interview". */
    private String stageName;
    private LocalDate interviewDate;
    private LocalTime interviewTime;
    private InterviewMode mode;
    private InterviewStatus status;
    private List<ScorecardSubmissionSummaryDto> submissions;
    /**
     * Whether the CALLER (not just any evaluator) is an assigned Interviewer
     * of this Interview or the Job's Hiring Manager - lets the FE hide the
     * "Cham diem" action entirely for a viewer (Recruiter, HR Admin, an
     * Interviewer not assigned to THIS Interview...) who would just get a
     * 403 if they clicked it. A UI hint only - the actual scoring endpoints
     * still enforce this for real via {@code ScorecardSubmissionService#checkEvaluatorEligible}.
     */
    private boolean currentUserCanScore;
}
