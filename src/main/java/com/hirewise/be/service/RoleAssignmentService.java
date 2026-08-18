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
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.security.KeycloakAdminClient;
import com.hirewise.be.security.UserDirectoryService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * UC-03: HR Admin gan Role va Access Scope cho tai khoan (ROLE_ASSIGN, chi
 * HR_ADMIN). Ca 2 thao tac (gan role & gan scope) cung nam sau 1 permission
 * duy nhat theo dung mo ta trong ma tran quyen muc 2.2: "ROLE_ASSIGN - Gan
 * Role VA pham vi phong ban/Job cho tai khoan".
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
    UserDirectoryService userDirectoryService;
    KeycloakAdminClient keycloakAdminClient;
    Clock clock;

    @Transactional
    public void assignRole(Long userId, AssignRoleRequestDto request, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.ROLE_ASSIGN, ResourceContext.none());

        User user = findUserOrThrow(userId);
        Role role = roleRepository.findByCode(request.getRoleCode())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.ROLE_NOT_FOUND, request.getRoleCode()));

        // Gan role THAT ben Keycloak TRUOC khi ghi ban ghi noi bo. Ly do thu
        // tu: role dung de CHO PHEP (RBAC layer 2, AccessControlService) doc
        // tu CurrentUser.roles() - tuc claim cua JWT - KHONG phai bang
        // user_roles ben duoi. Neu ghi DB truoc ma buoc goi Keycloak sau do
        // that bai, HR Admin se thay "da gan role" tren UI trong khi user
        // chua thuc su co quyen gi - dung dung thu tu nay de tranh dung dang
        // du lieu gay nham lan do. Loi o day (KeycloakSyncException, xem
        // KeycloakAdminClient#assignRealmRole) se lam @Transactional rollback,
        // khong ban ghi user_roles nao duoc tao.
        keycloakAdminClient.assignRealmRole(user.getKeycloakId(), role.getCode());

        Instant now = Instant.now(clock);
        UserRole userRole = UserRole.builder()
                .user(user)
                .role(role)
                .validFrom(request.getValidFrom() != null ? request.getValidFrom() : now)
                .validTo(request.getValidTo())
                .build();
        userRoleRepository.save(userRole);

        // userRoleRepository.save() o tren chi phuc vu hien thi/luu vet noi
        // bo (UserResponseDto.roleCodes) - nguon quyet dinh quyen that su la
        // Keycloak (buoc goi ben tren). evict() de UserSnapshot (userId,
        // status) khong giu ban ghi cu qua het TTL cho user nay.
        //
        // Luu y gioi han: role moi chi co hieu luc trong JWT MOI cua user
        // (dang nhap lai/refresh token) - phien dang nhap hien tai (neu co)
        // khong tu dong cap nhat quyen ngay lap tuc.
        userDirectoryService.evict(user.getKeycloakId());
        log.info("Assigned role {} to user {} (synced to Keycloak)", role.getCode(), userId);
    }

    /**
     * UC-03/AF-01 (Thu hoi quyen): doi ngau voi assignRole() - dong bo THU
     * HOI role tren Keycloak TRUOC (cung nguyen tac thu tu voi assignRole:
     * loi Keycloak thi rollback, khong dong bat ky ban ghi user_roles nao),
     * roi moi set valid_to=now cho MOI ban ghi user_roles dang hieu luc cua
     * role do (1 user co the duoc gan cung 1 role nhieu lan/nhieu khoang
     * thoi gian - dong het, khong chi ban ghi dau tien tim thay).
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

        keycloakAdminClient.revokeRealmRole(user.getKeycloakId(), roleCode);

        activeAssignments.forEach(ur -> ur.setValidTo(now));
        userRoleRepository.saveAll(activeAssignments);

        // Cung luu y gioi han nhu assignRole(): role bi thu hoi chi mat hieu
        // luc tu lan dang nhap lai/refresh token TIEP THEO cua user - phien
        // hien tai (neu con han) khong tu dong mat quyen ngay lap tuc, du
        // AuthenticationFreshnessFilter (Layer 1) van chan duoc neu tai
        // khoan bi Blocked hoan toan (khac voi thu hoi 1 role rieng le).
        userDirectoryService.evict(user.getKeycloakId());
        log.info("Revoked role {} from user {} (synced to Keycloak)", roleCode, userId);
    }

    @Transactional
    public UserAccessScopeResponseDto assignAccessScope(Long userId, AssignAccessScopeRequestDto request, CurrentUser currentUser) {
        accessControlService.checkAccess(currentUser, PermissionCodes.ROLE_ASSIGN, ResourceContext.none());

        User user = findUserOrThrow(userId);

        Department department = null;
        Long jobId = null;
        if (request.getScopeType() == ScopeType.DEPARTMENT) {
            if (request.getDepartmentId() == null) {
                throw new BadRequestException(ErrorCode.INVALID_INPUT);
            }
            department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.DEPARTMENT_NOT_FOUND, request.getDepartmentId()));
        } else if (request.getScopeType() == ScopeType.JOB) {
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
