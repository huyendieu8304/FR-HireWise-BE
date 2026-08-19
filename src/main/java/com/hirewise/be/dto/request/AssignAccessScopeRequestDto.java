package com.hirewise.be.dto.request;

import com.hirewise.be.domain.ScopeType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Request body for ROLE_ASSIGN (HR_ADMIN only) - assigns an access scope
 * (RBAC layer 3) to a user. Per BR-RBAC-05, a user can have multiple
 * scope_type=DEPARTMENT rows pointing to different departments at once;
 * call this endpoint multiple times to grant several.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignAccessScopeRequestDto {

    @NotNull(message = "{validation.access_scope.scope_type.required}")
    private ScopeType scopeType;

    /** Required when scopeType is DEPARTMENT. */
    private Long departmentId;

    /** Required when scopeType is JOB. */
    private UUID jobId;

    /** BR-RBAC-06: only meaningful when scopeType is DEPARTMENT. Defaults
     * to true (inherits access to sub-departments) if not supplied. */
    private Boolean includeSubDepartments;

    /** BR-RBAC-02: defaults to false (read-only) if not supplied. */
    private Boolean canWrite;

    private Instant validFrom;

    private Instant validTo;
}
