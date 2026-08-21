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
 * UC-02/UC-03: internal user account administration, plus role and access-scope
 * assignment.
 * <p>
 * Every endpoint in this controller is restricted to HR_ADMIN, enforced via
 * {@code AccessControlService} inside the delegate services rather than duplicated
 * as a role gate in {@code SecurityConfig} (see the {@code authorization} package).
 * <p>
 * RBAC per endpoint:
 * <ul>
 *   <li>{@code POST   /api/admin/users}                     - {@code USER_CREATE}</li>
 *   <li>{@code GET    /api/admin/users}                     - {@code USER_VIEW}</li>
 *   <li>{@code GET    /api/admin/users/{id}}                - {@code USER_VIEW}</li>
 *   <li>{@code PATCH  /api/admin/users/{id}/status}         - {@code USER_UPDATE}</li>
 *   <li>{@code POST   /api/admin/users/{id}/roles}          - {@code ROLE_ASSIGN}</li>
 *   <li>{@code DELETE /api/admin/users/{id}/roles/{roleCode}} - {@code ROLE_ASSIGN}</li>
 *   <li>{@code GET    /api/admin/users/{id}/access-scopes}  - {@code ROLE_ASSIGN}</li>
 *   <li>{@code POST   /api/admin/users/{id}/access-scopes}  - {@code ROLE_ASSIGN}</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/admin/users")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
public class UserAdminController {

    UserAdminService userAdminService;
    RoleAssignmentService roleAssignmentService;

    /**
     * Creates a new internal user account. Requires {@code USER_CREATE} (HR_ADMIN).
     *
     * @param request     new user data
     * @param currentUser authenticated caller, used for authorization and auditing
     * @return 201 Created with the created user and a {@code Location} header pointing
     *         to {@code GET /api/admin/users/{id}}
     */
    @PostMapping
    public ResponseEntity<UserResponseDto> create(
            @Valid @RequestBody CreateUserRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser
    ) {
        UserResponseDto response = userAdminService.create(request, currentUser);
        // Build the canonical resource URL for the newly created user so clients
        // can immediately GET it, per REST convention for 201 responses.
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.getId())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    /**
     * Searches internal user accounts. Requires {@code USER_VIEW} (HR_ADMIN).
     *
     * @param page        zero-based page index (defaults to 0)
     * @param size        page size (defaults to 20, capped between 1 and 100)
     * @param currentUser authenticated caller, used for authorization
     * @return a page of users ordered by id ascending
     */
    @GetMapping
    public ResponseEntity<PagedResponseDto<UserResponseDto>> search(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @CurrentUserPrincipal CurrentUser currentUser
    ) {
        // Clamp client-supplied paging params so callers can't request an
        // unbounded page size (DoS risk) or a negative page index.
        int boundedSize = Math.min(Math.max(size, 1), 100);
        int boundedPage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(boundedPage, boundedSize, Sort.by(Sort.Direction.ASC, "id"));
        return ResponseEntity.ok(userAdminService.search(pageable, currentUser));
    }

    /**
     * Retrieves a single internal user account by id. Requires {@code USER_VIEW} (HR_ADMIN).
     *
     * @param id          user id
     * @param currentUser authenticated caller, used for authorization
     * @return the requested user
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDto> getById(
            @PathVariable Long id,
            @CurrentUserPrincipal CurrentUser currentUser
    ) {
        return ResponseEntity.ok(userAdminService.getById(id, currentUser));
    }

    /**
     * Updates a user's account status (e.g. activate/deactivate). Requires
     * {@code USER_UPDATE} (HR_ADMIN).
     *
     * @param id          user id
     * @param request     new status data
     * @param currentUser authenticated caller, used for authorization and auditing
     * @return the updated user
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<UserResponseDto> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserStatusRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser
    ) {
        return ResponseEntity.ok(userAdminService.updateStatus(id, request, currentUser));
    }

    /**
     * Assigns a role to a user. Requires {@code ROLE_ASSIGN} (HR_ADMIN).
     *
     * @param id          user id to assign the role to
     * @param request     role to assign
     * @param currentUser authenticated caller, used for authorization and auditing
     * @return 204 No Content on success
     */
    @PostMapping("/{id}/roles")
    public ResponseEntity<Void> assignRole(
            @PathVariable Long id,
            @Valid @RequestBody AssignRoleRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser
    ) {
        roleAssignmentService.assignRole(id, request, currentUser);
        return ResponseEntity.noContent().build();
    }

    /**
     * Revokes a role from a user. Requires {@code ROLE_ASSIGN} (HR_ADMIN).
     *
     * @param id          user id to revoke the role from
     * @param roleCode    code of the role to revoke
     * @param currentUser authenticated caller, used for authorization and auditing
     * @return 204 No Content on success
     */
    @DeleteMapping("/{id}/roles/{roleCode}")
    public ResponseEntity<Void> revokeRole(
            @PathVariable Long id,
            @PathVariable String roleCode,
            @CurrentUserPrincipal CurrentUser currentUser
    ) {
        roleAssignmentService.revokeRole(id, roleCode, currentUser);
        return ResponseEntity.noContent().build();
    }

    /**
     * Lists the access scopes currently assigned to a user. Requires
     * {@code ROLE_ASSIGN} (HR_ADMIN).
     *
     * @param id          user id
     * @param currentUser authenticated caller, used for authorization
     * @return the user's assigned access scopes
     */
    @GetMapping("/{id}/access-scopes")
    public ResponseEntity<List<UserAccessScopeResponseDto>> listAccessScopes(
            @PathVariable Long id,
            @CurrentUserPrincipal CurrentUser currentUser
    ) {
        return ResponseEntity.ok(roleAssignmentService.listAccessScopes(id, currentUser));
    }

    /**
     * Assigns an access scope (e.g. department-level access) to a user. Requires
     * {@code ROLE_ASSIGN} (HR_ADMIN).
     *
     * @param id          user id to assign the access scope to
     * @param request     access scope to assign
     * @param currentUser authenticated caller, used for authorization and auditing
     * @return the created access scope
     */
    @PostMapping("/{id}/access-scopes")
    public ResponseEntity<UserAccessScopeResponseDto> assignAccessScope(
            @PathVariable Long id,
            @Valid @RequestBody AssignAccessScopeRequestDto request,
            @CurrentUserPrincipal CurrentUser currentUser
    ) {
        return ResponseEntity.ok(roleAssignmentService.assignAccessScope(id, request, currentUser));
    }
}
