package com.hirewise.be.authorization;

import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.repository.JobPositionRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * RBAC Layer 4 resolver for {@code resourceType = "JOB_POSITION"} - used by
 * UC-45 (publish), UC-44 (pause/close/resume) and UC-31 (share to external
 * channels), all of which require the acting Recruiter to be the Job's own
 * Recruiter (SRS 5.2.44 precondition, BR-JOB-04).
 *
 * <p>Unlike {@link ApplicationOwnershipResolver}, which has to walk up to the
 * parent Job to find an owner, a Job Position owns itself: {@code recruiter_id}
 * is the owner and {@code department_id} is the Layer 3 scope, both read off
 * the same row.</p>
 *
 * <p>HR Admin is not special-cased here. {@link OwnershipPolicyRegistry} never
 * declares an {@code (permission, "HR_ADMIN")} pair for these permissions, so
 * the ownership check is skipped for that role before this resolver's result
 * is ever compared - which is exactly the SRS rule ("Recruiter chủ Job hoặc
 * HR Admin").</p>
 */
@Component
public class JobPositionOwnershipResolver implements OwnershipResolver {

    private final JobPositionRepository jobPositionRepository;

    public JobPositionOwnershipResolver(JobPositionRepository jobPositionRepository) {
        this.jobPositionRepository = jobPositionRepository;
    }

    @Override
    public String resourceType() {
        return "JOB_POSITION";
    }

    /**
     * @param resourceId the Job Position's id, as a {@link UUID} (passed in by
     *                    {@link OwnershipAspect} from the controller's
     *                    {@code jobId} path variable)
     * @throws ResourceNotFoundException if no Job Position exists with this id
     */
    @Override
    public OwnedResource resolve(Object resourceId) {
        UUID jobId = (UUID) resourceId;
        JobPosition job = jobPositionRepository.findById(jobId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.JOB_POSITION_NOT_FOUND, jobId));

        Long ownerId = job.getRecruiter() != null ? job.getRecruiter().getId() : null;
        Long departmentId = job.getDepartment() != null ? job.getDepartment().getId() : null;
        return new OwnedResource(ownerId, departmentId, job.getId());
    }
}
