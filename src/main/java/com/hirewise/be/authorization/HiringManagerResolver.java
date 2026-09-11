package com.hirewise.be.authorization;

import com.hirewise.be.domain.JobPosition;
import com.hirewise.be.domain.User;
import com.hirewise.be.repository.UserRepository;
import com.hirewise.be.repository.UserRoleRepository;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * "Who is the Hiring Manager for this Job" - shared by every caller that
 * needs to know, since {@link JobPosition#getHiringManager()} is a dead
 * column nothing ever writes to (see {@code guides/04-DATABASE_DESIGN.md}
 * section 13). There is no single assigned-owner field; the system is
 * scope-based instead (originally established by
 * {@code JobService#notifyHiringManagers}, UC-13): any active
 * {@code HIRING_MANAGER} whose Access Scope covers the Job's department is
 * "the" Hiring Manager for it - possibly several, possibly none.
 */
@Component
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class HiringManagerResolver {

    UserRoleRepository userRoleRepository;
    UserRepository userRepository;
    AccessScopeService accessScopeService;
    Clock clock;

    /**
     * @param job     the Job whose Hiring Manager(s) are being resolved
     * @param requiresWrite {@code true} to only count Hiring Managers who could
     *                      actually ACT on this Job (matches {@code JOB_APPROVE}/
     *                      {@code SLA_CONFIGURE}'s write-scope check) - {@code false}
     *                      for a purely informational/display use (e.g. showing a
     *                      name on the Job Detail screen)
     * @return every Hiring Manager whose Access Scope covers the Job's department,
     *         possibly empty (nobody has been granted a scope covering it yet)
     */
    public List<User> resolveForJob(JobPosition job, boolean requiresWrite) {
        Instant now = Instant.now(clock);
        List<Long> hiringManagerIds = userRoleRepository.findActiveUserIdsByRoleCode("HIRING_MANAGER", now);
        if (hiringManagerIds.isEmpty()) {
            return List.of();
        }

        Long departmentId = job.getDepartment() != null ? job.getDepartment().getId() : null;
        ResourceContext jobResource = ResourceContext.department(departmentId);
        return userRepository.findAllById(hiringManagerIds).stream()
                .filter(user -> accessScopeService.isWithinScope(user.getId(), jobResource, requiresWrite))
                .toList();
    }
}
