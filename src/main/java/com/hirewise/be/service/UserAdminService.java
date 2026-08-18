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
 * UC-02: HR Admin quan ly tai khoan noi bo (USER_CREATE/USER_UPDATE/USER_VIEW).
 * create() tu goi KeycloakAdminClient de tao that tai khoan Keycloak (+ gui
 * email kich hoat EM-01) TRUOC, roi moi ghi ban ghi `users` noi bo lien ket
 * voi keycloakId vua tao - can thiet de RBAC hoat dong (BR-AUTH-07,
 * BR-RBAC-05). Neu buoc ghi DB that bai sau khi Keycloak da tao xong, goi
 * compensating action (deleteUser) de khong bo lai user "mo coi" ben Keycloak.
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

    @Transactional
    public UserResponseDto create(CreateUserRequestDto request, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.USER_CREATE, ResourceContext.none());

        // check email đã duoc su dung cho tk nao truoc do chua
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BusinessConflictException(ErrorCode.USER_ALREADY_EXISTS);
        }

        // tim department de gan vao user
        Department department = resolveDepartmentOrNull(request.getDepartmentId());

        // Tao user THAT ben Keycloak TRUOC khi ghi ban ghi noi bo (nguoc lai
        // se co 1 ban ghi `users` "ma" khong dang nhap duoc neu Keycloak loi
        // ma khong co cach nao bao ngay cho HR Admin). Loi o day
        // (KeycloakSyncException) lam @Transactional rollback, khong ban ghi
        // `users` nao duoc tao - xem KeycloakAdminClient#createUser.
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
            // Compensate: don user "mo coi" ben Keycloak vi buoc ghi DB noi bo
            // that bai ngay sau khi Keycloak da tao xong - giu 2 nguon du lieu
            // nhat quan (best-effort, khong nuot loi goc de @Transactional van
            // rollback binh thuong).
            keycloakAdminClient.deleteUser(keycloakId);
            throw dbFailure;
        }
    }

    public UserResponseDto getById(Long id, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.USER_VIEW, ResourceContext.none());
        User user = findOrThrow(id);
        return UserMapper.toResponseDto(user, activeRoleCodes(id));
    }

    public PagedResponseDto<UserResponseDto> search(Pageable pageable, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.USER_VIEW, ResourceContext.none());
        Page<User> page = userRepository.findAll(pageable);
        List<UserResponseDto> content = page.getContent().stream()
                .map(user -> UserMapper.toResponseDto(user, activeRoleCodes(user.getId())))
                .toList();
        return PagedResponseDto.from(page, content);
    }

    @Transactional
    public UserResponseDto updateStatus(Long id, UpdateUserStatusRequestDto request, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.USER_UPDATE, ResourceContext.none());

        User user = findOrThrow(id);
        user.setStatus(request.getStatus());
        user.setUpdatedAt(Instant.now(clock));
        userRepository.save(user);

        // revoke user
        userDirectoryService.evict(user.getKeycloakId());
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
