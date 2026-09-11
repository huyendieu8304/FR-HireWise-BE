package com.hirewise.be.authorization;

import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.ScorecardSubmission;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.repository.ScorecardSubmissionRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * RBAC Layer 4 resolver for {@code resourceType = "SCORECARD_SUBMISSION"}
 * (UC-28, {@code SCORECARD_SUBMIT requiresOwnership=true} for both
 * INTERVIEWER and HIRING_MANAGER - see {@link OwnershipPolicyRegistry}).
 * <p>
 * A {@link ScorecardSubmission} has exactly ONE evaluator - unlike
 * {@code APPLICATION}/{@code JOB_POSITION} whose owner is inherited from a
 * parent, {@link ScorecardSubmission#getEvaluator()} IS the owner directly:
 * you may only save/submit your OWN rating of an Interview, never someone
 * else's (even a fellow Interviewer on the same panel).
 */
@Component
public class ScorecardSubmissionOwnershipResolver implements OwnershipResolver {

    private final ScorecardSubmissionRepository scorecardSubmissionRepository;

    public ScorecardSubmissionOwnershipResolver(ScorecardSubmissionRepository scorecardSubmissionRepository) {
        this.scorecardSubmissionRepository = scorecardSubmissionRepository;
    }

    @Override
    public String resourceType() {
        return "SCORECARD_SUBMISSION";
    }

    /**
     * @param resourceId the Submission's id, as a {@link UUID} (from the
     *                    controller's {@code submissionId} path variable)
     * @throws ResourceNotFoundException if no submission exists with this id
     */
    @Override
    public OwnedResource resolve(Object resourceId) {
        UUID submissionId = (UUID) resourceId;
        ScorecardSubmission submission = scorecardSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.SCORECARD_SUBMISSION_NOT_FOUND, submissionId));

        Application application = submission.getInterview().getApplication();
        JobPosition job = application.getJobPosition();
        Long departmentId = job.getDepartment() != null ? job.getDepartment().getId() : null;
        return new OwnedResource(submission.getEvaluator().getId(), departmentId, job.getId());
    }
}
