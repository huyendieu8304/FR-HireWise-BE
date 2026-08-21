package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.Department;
import com.hirewise.be.domain.Role;
import com.hirewise.be.domain.ScopeType;
import com.hirewise.be.domain.User;
import com.hirewise.be.domain.UserAccessScope;
import com.hirewise.be.domain.UserRole;
import com.hirewise.be.dto.request.AssignAccessScopeRequestDto;
import com.hirewise.be.dto.request.AssignRoleRequestDto;
import com.hirewise.be.dto.response.UserAccessScopeResponseDto;
import com.hirewise.be.exception.BadRequestException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.mapper.UserMapper;
import com.hirewise.be.repository.DepartmentRepository;
import com.hirewise.be.repository.RoleRepository;
import com.hirewise.be.repository.UserAccessScopeRepository;
import com.hirewise.be.repository.UserRepository;
import com.hirewise.be.repository.UserRoleRepository;
import com.hirewise.be.security.ActiveRolesService;
import com.hirewise.be.security.CurrentUser;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * UC-03: lets an HR Admin assign roles and access scopes to accounts - permission {@code ROLE_ASSIGN}, HR_ADMIN only.
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class RoleAssignmentService {

    UserRepository userRepository;
    RoleRepository roleRepository;
    DepartmentRepository departmentRepository;
    UserRoleRepository userRoleRepository;
    UserAccessScopeRepository userAccessScopeRepository;
    AccessControlService accessControlService;
    ActiveRolesService activeRolesService;
    Clock clock;

    /**
     * assigns a role to a user.
     *
     * @param userId      id of the user receiving the role
     * @param request     role code plus optional validity window
     * @param currentUser HR Admin performing the assignment
     * @throws ResourceNotFoundException if the user or role code cannot be found
     */
    @Transactional
    public void assignRole(Long userId, AssignRoleRequestDto request, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.ROLE_ASSIGN, ResourceContext.none());

        User user = findUserOrThrow(userId);
        Role role = roleRepository.findByCode(request.getRoleCode())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND, request.getRoleCode()));

        Instant now = Instant.now(clock);
        UserRole userRole = UserRole.builder()
                .user(user)
                .role(role)
                .validFrom(request.getValidFrom() != null ? request.getValidFrom() : now)
                .validTo(request.getValidTo())
                .build();
        userRoleRepository.save(userRole);

        // ActiveRolesService resolves roles live from user_roles (short-TTL
        // cache) rather than baking them into the access token at login
        // time, so evicting here makes the new role take effect within
        // seconds - not only after the user's current token naturally
        // expires or they log in again.
        activeRolesService.evict(userId);
        log.info("Assigned role {} to user {}", role.getCode(), userId);
    }

    /**
     * (revoke access): the mirror image of {@link #assignRole};
     * sets {@code validTo=now} on EVERY currently-active {@code user_roles}
     * record for that role (a user can be assigned the same role multiple
     * times over different validity windows - all of them are closed out,
     * not just the first one found).
     *
     * @param userId      id of the user losing the role
     * @param roleCode    code of the role to revoke
     * @param currentUser HR Admin performing the revocation
     * @throws ResourceNotFoundException if the user cannot be found, or if the
     *                                    user has no currently-active assignment
     *                                    of {@code roleCode}
     */
    @Transactional
    public void revokeRole(Long userId, String roleCode, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.ROLE_ASSIGN, ResourceContext.none());

        User user = findUserOrThrow(userId);
        Instant now = Instant.now(clock);

        List<UserRole> activeAssignments = userRoleRepository.findByUserId(userId).stream()
                .filter(ur -> ur.getRole().getCode().equals(roleCode))
                .filter(ur -> ur.getValidTo() == null || ur.getValidTo().isAfter(now))
                .toList();

        if (activeAssignments.isEmpty()) {
            throw new ResourceNotFoundException(ErrorCode.ROLE_NOT_ASSIGNED, roleCode);
        }

        activeAssignments.forEach(ur -> ur.setValidTo(now));
        userRoleRepository.saveAll(activeAssignments);

        activeRolesService.evict(userId);
        log.info("Revoked role {} from user {}", roleCode, userId);
    }

    /**
     * assigns an access scope (System/Department/Job-level) to a
     * user. BR-RBAC-05 (multi-department) and BR-RBAC-06 (sub-department
     * inheritance via {@code includeSubDepartments}) are represented as-is
     * on the created row; a user may hold several scope rows at once, one
     * call per department/job.
     *
     * @param userId      id of the user receiving the scope
     * @param request     scope type plus the target department/job and validity window
     * @param currentUser HR Admin performing the assignment
     * @return the created access scope
     * @throws ResourceNotFoundException if the user or the referenced department cannot be found
     * @throws BadRequestException       if the scope type is DEPARTMENT/JOB but the
     *                                    corresponding id is missing from the request
     */
    @Transactional
    public UserAccessScopeResponseDto assignAccessScope(Long userId, AssignAccessScopeRequestDto request, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.ROLE_ASSIGN, ResourceContext.none());

        User user = findUserOrThrow(userId);

        Department department = null;
        UUID jobId = null;
        if (request.getScopeType() == ScopeType.DEPARTMENT) {
            // A department-scoped grant must point at a real department.
            if (request.getDepartmentId() == null) {
                throw new BadRequestException(ErrorCode.INVALID_INPUT);
            }
            department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.DEPARTMENT_NOT_FOUND, request.getDepartmentId()));
        } else if (request.getScopeType() == ScopeType.JOB) {
            // A job-scoped grant must point at a specific job id.
            if (request.getJobId() == null) {
                throw new BadRequestException(ErrorCode.INVALID_INPUT);
            }
            jobId = request.getJobId();
        }

        Instant now = Instant.now(clock);
        UserAccessScope scope = UserAccessScope.builder()
                .user(user)
                .scopeType(request.getScopeType())
                .department(department)
                .jobId(jobId)
                .includeSubDepartments(request.getIncludeSubDepartments() == null || request.getIncludeSubDepartments())
                .canWrite(request.getCanWrite() != null && request.getCanWrite())
                .validFrom(request.getValidFrom() != null ? request.getValidFrom() : now)
                .validTo(request.getValidTo())
                .build();
        userAccessScopeRepository.save(scope);

        log.info("Assigned access scope {} ({}) to user {}", scope.getId(), scope.getScopeType(), userId);
        return UserMapper.toResponseDto(scope);
    }

    /**
     * Lists all access scopes assigned to a user.
     *
     * @param userId      id of the user whose scopes are being listed
     * @param currentUser user requesting access; must have view permission
     * @return the user's access scopes; empty if none are assigned
     * @throws ResourceNotFoundException if the user cannot be found
     */
    public List<UserAccessScopeResponseDto> listAccessScopes(Long userId, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.USER_VIEW, ResourceContext.none());
        findUserOrThrow(userId);
        return userAccessScopeRepository.findByUserId(userId).stream()
                .map(UserMapper::toResponseDto)
                .toList();
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, userId));
    }
}
