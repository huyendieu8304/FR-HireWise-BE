package com.hirewise.be.controller;

import com.hirewise.be.dto.PagedResponseDto;
import com.hirewise.be.dto.request.AssignAccessScopeRequestDto;
import com.hirewise.be.dto.request.AssignRoleRequestDto;
import com.hirewise.be.dto.request.CreateUserRequestDto;
import com.hirewise.be.dto.request.UpdateUserStatusRequestDto;
import com.hirewise.be.dto.response.UserAccessScopeResponseDto;
import com.hirewise.be.dto.response.UserResponseDto;
import com.hirewise.be.security.CurrentUser;
import com.hirewise.be.security.CurrentUserPrincipal;
import com.hirewise.be.service.RoleAssignmentService;
import com.hirewise.be.service.UserAdminService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * UC-02/UC-03: quan tri tai khoan noi bo + gan Role/Access Scope - toan bo
 * endpoint chi danh cho HR_ADMIN (USER_CREATE/USER_UPDATE/USER_VIEW/
 * ROLE_ASSIGN), thuc thi qua AccessControlService trong service lien quan
 * (khong lap lai role gate o SecurityConfig - xem package authorization).
 */
@RestController
@RequestMapping("/api/admin/users")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class UserAdminController {

    UserAdminService userAdminService;
    RoleAssignmentService roleAssignmentService;

    @PostMapping
    public ResponseEntity<UserResponseDto> create(
            @Valid @RequestBody CreateUserRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser
    ) {
        UserResponseDto response = userAdminService.create(request, currentUser);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getId())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping
    public ResponseEntity<PagedResponseDto<UserResponseDto>> search(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @CurrentUserPrincipal CurrentUser currentUser
    ) {
        int boundedSize = Math.min(Math.max(size, 1), 100);
        int boundedPage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(boundedPage, boundedSize, Sort.by(Sort.Direction.ASC, "id"));
        return ResponseEntity.ok(userAdminService.search(pageable, currentUser));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDto> getById(
            @PathVariable Long id,
            @CurrentUserPrincipal CurrentUser currentUser
    ) {
        return ResponseEntity.ok(userAdminService.getById(id, currentUser));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<UserResponseDto> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser
    ) {
        return ResponseEntity.ok(userAdminService.updateStatus(id, request, currentUser));
    }

    @PostMapping("/{id}/roles")
    public ResponseEntity<Void> assignRole(
            @PathVariable Long id,
            @Valid @RequestBody AssignRoleRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser
    ) {
        roleAssignmentService.assignRole(id, request, currentUser);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}/roles/{roleCode}")
    public ResponseEntity<Void> revokeRole(
            @PathVariable Long id,
            @PathVariable String roleCode,
            @CurrentUserPrincipal CurrentUser currentUser
    ) {
        roleAssignmentService.revokeRole(id, roleCode, currentUser);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/access-scopes")
    public ResponseEntity<List<UserAccessScopeResponseDto>> listAccessScopes(
            @PathVariable Long id,
            @CurrentUserPrincipal CurrentUser currentUser
    ) {
        return ResponseEntity.ok(roleAssignmentService.listAccessScopes(id, currentUser));
    }

    @PostMapping("/{id}/access-scopes")
    public ResponseEntity<UserAccessScopeResponseDto> assignAccessScope(
            @PathVariable Long id,
            @Valid @RequestBody AssignAccessScopeRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser
    ) {
        return ResponseEntity.ok(roleAssignmentService.assignAccessScope(id, request, currentUser));
    }
}
