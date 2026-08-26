package com.hirewise.be.authorization;

import com.hirewise.be.domain.Application;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.repository.ApplicationRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * RBAC Layer 4 resolver for {@code resourceType = "APPLICATION"}
 * (RBAC.md section 4: {@code APPLICATION_MOVE_STAGE}/{@code APPLICATION_REJECT}
 * both {@code requiresOwnership = true} for RECRUITER). An Application has
 * no owner field of its own - ownership is inherited through its parent
 * {@link JobPosition}: {@code application.job.recruiter_id} (RBAC.md line
 * 188), exactly like the Access Scope department/job also come from the
 * parent Job rather than the Application itself.
 */
@Component
public class ApplicationOwnershipResolver implements OwnershipResolver {

    private final ApplicationRepository applicationRepository;

    public ApplicationOwnershipResolver(ApplicationRepository applicationRepository) {
        this.applicationRepository = applicationRepository;
    }

    @Override
    public String resourceType() {
        return "APPLICATION";
    }

    /**
     * @param resourceId the Application's id, as a {@link UUID} (passed in
     *                    by {@link OwnershipAspect} from the controller's
     *                    {@code applicationId} path variable)
     * @throws ResourceNotFoundException if no Application exists with this id
     */
    @Override
    public OwnedResource resolve(Object resourceId) {
        UUID applicationId = (UUID) resourceId;
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.APPLICATION_NOT_FOUND, applicationId));

        JobPosition job = application.getJobPosition();
        Long ownerId = job.getRecruiter() != null ? job.getRecruiter().getId() : null;
        Long departmentId = job.getDepartment() != null ? job.getDepartment().getId() : null;
        return new OwnedResource(ownerId, departmentId, job.getId());
    }
}
