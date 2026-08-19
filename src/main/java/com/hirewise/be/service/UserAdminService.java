package com.hirewise.be.service;

import com.hirewise.be.authorization.AccessControlService;
import com.hirewise.be.authorization.PermissionCodes;
import com.hirewise.be.authorization.ResourceContext;
import com.hirewise.be.security.token.ActivationToken;
import com.hirewise.be.security.token.ActivationTokenPurpose;
import com.hirewise.be.domain.Department;
import com.hirewise.be.event.OutboxEventType;
import com.hirewise.be.event.OutboxEventPublisher;
import com.hirewise.be.domain.User;
import com.hirewise.be.domain.UserSession;
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
import com.hirewise.be.repository.ActivationTokenRepository;
import com.hirewise.be.repository.DepartmentRepository;
import com.hirewise.be.repository.UserRepository;
import com.hirewise.be.repository.UserRoleRepository;
import com.hirewise.be.repository.UserSessionRepository;
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.security.token.OpaqueTokenUtil;
import com.hirewise.be.security.SessionRegistryService;
import com.hirewise.be.security.UserDirectoryService;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * lets an HR Admin manage internal accounts (USER_CREATE/USER_UPDATE/ USER_VIEW)
 * <p>
 * {@link #create} provisions the account directly as {@code INVITED} (no
 * usable password yet) and enqueues the EM-01 activation email via the
 * transactional outbox, atomically with the {@code users} row - there is no
 * external system to keep in sync anymore, so no compensating action is
 * needed on failure; a rollback of the {@code @Transactional} method is
 * enough.
 * <p>
 * {@link #updateStatus} moving a user OUT of
 * {@code ACTIVE} immediately revokes every one of their currently-active
 * sessions (not just future logins), and evicts the short-TTL
 * {@code UserDirectoryService} cache entry so RBAC layer 1
 * (Authentication Freshness, BR-AUTH-07) picks up the change on the very
 * next request instead of waiting out the cache TTL.
 * <p>
 * there is deliberately no delete method here - accounts are
 * only ever moved between statuses, never hard-deleted.
 */
@Slf4j
@Service
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserAdminService {

    UserRepository userRepository;
    DepartmentRepository departmentRepository;
    UserRoleRepository userRoleRepository;
    ActivationTokenRepository activationTokenRepository;
    UserSessionRepository userSessionRepository;
    OutboxEventPublisher outboxEventPublisher;
    AccessControlService accessControlService;
    UserDirectoryService userDirectoryService;
    SessionRegistryService sessionRegistryService;
    PasswordEncoder passwordEncoder;
    Clock clock;

    long activationTokenTtlHours;
    String activationLinkBaseUrl;

    public UserAdminService(UserRepository userRepository,
                             DepartmentRepository departmentRepository,
                             UserRoleRepository userRoleRepository,
                             ActivationTokenRepository activationTokenRepository,
                             UserSessionRepository userSessionRepository,
                             OutboxEventPublisher outboxEventPublisher,
                             AccessControlService accessControlService,
                             UserDirectoryService userDirectoryService,
                             SessionRegistryService sessionRegistryService,
                             PasswordEncoder passwordEncoder,
                             Clock clock,
                             @Value("${app.activation.token-ttl-hours:72}") long activationTokenTtlHours,
                             @Value("${app.activation.link-base-url:http://localhost:3000/activate}") String activationLinkBaseUrl) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.userRoleRepository = userRoleRepository;
        this.activationTokenRepository = activationTokenRepository;
        this.userSessionRepository = userSessionRepository;
        this.outboxEventPublisher = outboxEventPublisher;
        this.accessControlService = accessControlService;
        this.userDirectoryService = userDirectoryService;
        this.sessionRegistryService = sessionRegistryService;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.activationTokenTtlHours = activationTokenTtlHours;
        this.activationLinkBaseUrl = activationLinkBaseUrl;
    }

    /**
     *  creates a new internal user account in status {@code INVITED} and
     *  enqueues the EM-01 activation email carrying a one-time "set your password" link.
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

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessConflictException(ErrorCode.USER_ALREADY_EXISTS);
        }

        Department department = resolveDepartmentOrNull(request.getDepartmentId());
        Instant now = Instant.now(clock);

        User user = User.builder()
                .email(request.getEmail())
                .fullName(request.getFullName())
                .department(department)
                .status(UserStatus.INVITED)
                .createdAt(now)
                .updatedAt(now)
                .build();
        userRepository.save(user);

        String rawToken = issueActivationToken(user, now);
        String activationLink = activationLinkBaseUrl + "?token=" + rawToken;
        outboxEventPublisher.publish(OutboxEventType.ACTIVATION_EMAIL, Map.of(
                "email", user.getEmail(),
                "fullName", user.getFullName() == null ? "" : user.getFullName(),
                "activationLink", activationLink));

        log.info("Created internal user: {} (email={}, status=INVITED)",
                user.getId(), LogMaskUtils.maskEmail(user.getEmail()));
        return UserMapper.toResponseDto(user, Set.of());
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
     * (block/unblock): updates a user's account status.
     * whenever the new status is anything other than {@code ACTIVE}, every
     * currently-active session of the user is revoked immediately, in the
     * same transaction as the status change.
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
        Instant now = Instant.now(clock);
        user.setStatus(request.getStatus());
        user.setUpdatedAt(now);
        userRepository.save(user);

        // RBAC layer 1 (BR-AUTH-07) must see the new status on the very next
        // request, not after the cache TTL lapses.
        userDirectoryService.evict(user.getId());

        // BR-AUTH-04: any non-ACTIVE status must immediately end every
        // session the user currently holds, not just block future logins.
        if (request.getStatus() != UserStatus.ACTIVE) {
            List<UserSession> activeSessions = userSessionRepository.findByUserIdAndRevokedAtIsNull(id);
            if (!activeSessions.isEmpty()) {
                activeSessions.forEach(session -> session.setRevokedAt(now));
                userSessionRepository.saveAll(activeSessions);
                activeSessions.forEach(session -> sessionRegistryService.evict(session.getSessionId()));
            }
        }

        log.info("Updated user status: {} -> {}", id, request.getStatus());
        return UserMapper.toResponseDto(user, activeRoleCodes(id));
    }

    private String issueActivationToken(User user, Instant now) {
        UUID tokenId = UUID.randomUUID();
        String secret = OpaqueTokenUtil.newSecret();
        ActivationToken token = ActivationToken.builder()
                .tokenId(tokenId)
                .user(user)
                .tokenHash(passwordEncoder.encode(secret))
                .purpose(ActivationTokenPurpose.ACTIVATION)
                .expiresAt(now.plus(Duration.ofHours(activationTokenTtlHours)))
                .createdAt(now)
                .build();
        activationTokenRepository.save(token);
        return OpaqueTokenUtil.encode(tokenId, secret);
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
