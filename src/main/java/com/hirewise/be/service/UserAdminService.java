package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.domain.Department;
import com.hirewise.be.domain.User;
import com.hirewise.be.domain.UserStatus;
import com.hirewise.be.dto.PagedResponseDto;
import com.hirewise.be.dto.request.CreateUserRequestDto;
import com.hirewise.be.dto.request.UpdateUserStatusRequestDto;
import com.hirewise.be.dto.response.UserResponseDto;
import com.hirewise.be.exception.BusinessConflictException;
import com.hirewise.be.exception.ErrorCode;
import com.hirewise.be.exception.ResourceNotFoundException;
import com.hirewise.be.logging.LogMaskUtils;
import com.hirewise.be.mapper.UserMapper;
import com.hirewise.be.repository.DepartmentRepository;
import com.hirewise.be.repository.UserRepository;
import com.hirewise.be.repository.UserRoleRepository;
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.security.KeycloakAdminClient;
import com.hirewise.be.security.UserDirectoryService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * UC-02: lets an HR Admin manage internal accounts
 * (USER_CREATE/USER_UPDATE/USER_VIEW). {@link #create} calls
 * {@code KeycloakAdminClient} to actually create the Keycloak account (and
 * send the EM-01 activation email) FIRST, then writes the internal `users`
 * record linked to the newly created keycloakId - required for RBAC to work
 * (BR-AUTH-07, BR-RBAC-05). If the DB write fails after Keycloak has already
 * created the account, a compensating action ({@code deleteUser}) is invoked
 * so an "orphaned" user is not left behind in Keycloak.
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class UserAdminService {

    UserRepository userRepository;
    DepartmentRepository departmentRepository;
    UserRoleRepository userRoleRepository;
    AccessControlService accessControlService;
    UserDirectoryService userDirectoryService;
    KeycloakAdminClient keycloakAdminClient;
    Clock clock;

    /**
     * UC-02: creates a new internal user account, provisioning it in
     * Keycloak before persisting the local record.
     *
     * @param request     new user's email, full name and optional department
     * @param currentUser HR Admin performing the creation
     * @return the created user
     * @throws BusinessConflictException if the email is already in use
     * @throws ResourceNotFoundException if the requested department cannot be found
     */
    @Transactional
    public UserResponseDto create(CreateUserRequestDto request, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.USER_CREATE, ResourceContext.none());

        // Reject duplicate accounts before touching Keycloak.
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessConflictException(ErrorCode.USER_ALREADY_EXISTS);
        }

        Department department = resolveDepartmentOrNull(request.getDepartmentId());

        // Create the account in Keycloak FIRST, before writing the local
        // record (the reverse order would risk a "ghost" `users` record that
        // can't log in if Keycloak fails, with no immediate way to notify the
        // HR Admin). A failure here (KeycloakSyncException) rolls back the
        // @Transactional method, so no `users` record gets created - see
        // KeycloakAdminClient#createUser.
        String keycloakId = keycloakAdminClient.createUser(request.getEmail(), request.getFullName());

        Instant now = Instant.now(clock);
        try {
            User user = User.builder()
                    .keycloakId(keycloakId)
                    .email(request.getEmail())
                    .fullName(request.getFullName())
                    .department(department)
                    .status(UserStatus.ACTIVE)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            userRepository.save(user);

            log.info("Created internal user: {} (email={}, keycloakId={})",
                    user.getId(), LogMaskUtils.maskEmail(user.getEmail()), keycloakId);
            return UserMapper.toResponseDto(user, Set.of());
        } catch (RuntimeException dbFailure) {
            // Compensating action: remove the now-orphaned Keycloak user
            // since the local DB write failed right after Keycloak succeeded
            // - keeps the two data sources consistent (best-effort; the
            // original exception is rethrown, not swallowed, so
            // @Transactional still rolls back normally).
            keycloakAdminClient.deleteUser(keycloakId);
            throw dbFailure;
        }
    }

    /**
     * Retrieves a single user by id, including their currently active role codes.
     *
     * @param id          user id
     * @param currentUser user requesting access; must have view permission
     * @return the user
     * @throws ResourceNotFoundException if no user exists with {@code id}
     */
    public UserResponseDto getById(Long id, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.USER_VIEW, ResourceContext.none());
        User user = findOrThrow(id);
        return UserMapper.toResponseDto(user, activeRoleCodes(id));
    }

    /**
     * Searches all internal users with pagination.
     *
     * @param pageable    pagination/sort parameters
     * @param currentUser user requesting access; must have view permission
     * @return a page of users, each including their currently active role codes
     */
    public PagedResponseDto<UserResponseDto> search(Pageable pageable, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.USER_VIEW, ResourceContext.none());
        Page<User> page = userRepository.findAll(pageable);
        List<UserResponseDto> content = page.getContent().stream()
                .map(user -> UserMapper.toResponseDto(user, activeRoleCodes(user.getId())))
                .toList();
        return PagedResponseDto.from(page, content);
    }

    /**
     * Updates a user's status (e.g. active/blocked). Evicts the cached
     * Keycloak session snapshot, and force-logs-out the user in Keycloak
     * whenever the new status is anything other than ACTIVE.
     *
     * @param id          user id
     * @param request     the new status
     * @param currentUser HR Admin performing the update
     * @return the updated user
     * @throws ResourceNotFoundException if no user exists with {@code id}
     */
    @Transactional
    public UserResponseDto updateStatus(Long id, UpdateUserStatusRequestDto request, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.USER_UPDATE, ResourceContext.none());

        User user = findOrThrow(id);
        user.setStatus(request.getStatus());
        user.setUpdatedAt(Instant.now(clock));
        userRepository.save(user);

        userDirectoryService.evict(user.getKeycloakId());
        // Any non-ACTIVE status (e.g. blocked) must immediately end the
        // user's current session, not just prevent future logins.
        if (request.getStatus() != UserStatus.ACTIVE) {
            keycloakAdminClient.forceLogout(user.getKeycloakId());
        }

        log.info("Updated user status: {} -> {}", id, request.getStatus());
        return UserMapper.toResponseDto(user, activeRoleCodes(id));
    }

    private Set<String> activeRoleCodes(Long userId) {
        return new HashSet<>(userRoleRepository.findActiveRoleCodes(userId, Instant.now(clock)));
    }

    private Department resolveDepartmentOrNull(Long departmentId) {
        if (departmentId == null) {
            return null;
        }
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.DEPARTMENT_NOT_FOUND, departmentId));
    }

    private User findOrThrow(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.USER_NOT_FOUND, id));
    }
}
