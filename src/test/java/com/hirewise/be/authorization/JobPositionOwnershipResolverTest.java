package com.hirewise.be.authorization;

import com.hirewise.be.domain.Department;
import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.User;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.repository.JobPositionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * RBAC Layer 4 resolver cho Job Position (UC-45/UC-31/UC-44). Bao phu: doc
 * dung owner = recruiter cua chinh Job, doc dung department cho Layer 3, va
 * hanh vi khi Job chua gan recruiter/department (ca hai deu nullable trong
 * V8) - null owner phai duoc tra ve nguyen ven de OwnershipAspect tu quyet
 * dinh, khong duoc nem NullPointerException.
 */
@ExtendWith(MockitoExtension.class)
class JobPositionOwnershipResolverTest {

    @Mock
    private JobPositionRepository jobPositionRepository;

    @InjectMocks
    private JobPositionOwnershipResolver resolver;

    private static final UUID JOB_ID = UUID.randomUUID();

    private JobPosition job(Long recruiterId, Long departmentId) {
        JobPosition job = new JobPosition();
        job.setId(JOB_ID);
        if (recruiterId != null) {
            User recruiter = new User();
            recruiter.setId(recruiterId);
            job.setRecruiter(recruiter);
        }
        if (departmentId != null) {
            Department department = new Department();
            department.setId(departmentId);
            job.setDepartment(department);
        }
        return job;
    }

    @Test
    void resourceTypeMatchesTheAnnotationValue() {
        assertThat(resolver.resourceType()).isEqualTo("JOB_POSITION");
    }

    @Test
    void resolveReturnsRecruiterAsOwnerAndDepartmentAsScope() {
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job(5L, 10L)));

        OwnedResource resolved = resolver.resolve(JOB_ID);

        assertThat(resolved.ownerId()).isEqualTo(5L);
        assertThat(resolved.departmentId()).isEqualTo(10L);
        assertThat(resolved.jobId()).isEqualTo(JOB_ID);
        assertThat(resolved.toResourceContext()).isEqualTo(new ResourceContext(10L, JOB_ID));
    }

    @Test
    void resolveReturnsNullOwnerWhenJobHasNoRecruiterYet() {
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job(null, 10L)));

        OwnedResource resolved = resolver.resolve(JOB_ID);

        assertThat(resolved.ownerId()).isNull();
        assertThat(resolved.departmentId()).isEqualTo(10L);
    }

    @Test
    void resolveReturnsNullDepartmentWhenJobHasNoDepartment() {
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.of(job(5L, null)));

        assertThat(resolver.resolve(JOB_ID).departmentId()).isNull();
    }

    @Test
    void unknownJobIdThrowsResourceNotFound() {
        when(jobPositionRepository.findById(JOB_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(JOB_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    /**
     * Layer 4 chi duoc kich hoat cho RECRUITER. HR_ADMIN khong khai bao trong
     * {@link OwnershipPolicyRegistry} nen phai mac dinh la "khong can ownership"
     * - dung dieu kien "Recruiter chu Job HOAC HR Admin" cua SRS 5.2.44.
     */
    @Test
    void policyRequiresOwnershipForRecruiterButNotForHrAdmin() {
        OwnershipPolicyRegistry policy = new OwnershipPolicyRegistry();

        assertThat(policy.requiresOwnership(PermissionCodes.JOB_CLOSE_PAUSE, Set.of("RECRUITER"))).isTrue();
        assertThat(policy.requiresOwnership(PermissionCodes.JOB_CLOSE_PAUSE, Set.of("HR_ADMIN"))).isFalse();
        assertThat(policy.requiresOwnership(PermissionCodes.JOB_PUBLISH, Set.of("RECRUITER"))).isTrue();
        // Recruiter kiem HR Admin: role rong hon thang, khong bi chan ownership.
        assertThat(policy.requiresOwnership(
                PermissionCodes.JOB_CLOSE_PAUSE, Set.of("RECRUITER", "HR_ADMIN"))).isFalse();
    }
}
