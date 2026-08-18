package com.hirewise.be.dto.request;

import com.hirewise.be.domain.ScopeType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * ROLE_ASSIGN (chi HR_ADMIN) - gan pham vi truy cap (RBAC layer 3) cho 1
 * user. BR-RBAC-05: 1 user co the co nhieu dong scope_type=DEPARTMENT tro
 * toi nhieu phong ban khac nhau cung luc - goi endpoint nay nhieu lan.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignAccessScopeRequestDto {

    @NotNull(message = "{validation.access_scope.scope_type.required}")
    private ScopeType scopeType;

    /** Bat buoc khi scopeType=DEPARTMENT. */
    private Long departmentId;

    /** Bat buoc khi scopeType=JOB. */
    private Long jobId;

    /** BR-RBAC-06 - chi co y nghia khi scopeType=DEPARTMENT. Mac dinh true
     * (ke thua xuong phong ban con) neu khong truyen. */
    private Boolean includeSubDepartments;

    /** BR-RBAC-02 - mac dinh false (chi xem) neu khong truyen. */
    private Boolean canWrite;

    private Instant validFrom;

    private Instant validTo;
}
