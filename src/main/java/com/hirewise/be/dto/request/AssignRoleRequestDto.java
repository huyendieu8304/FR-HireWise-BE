package com.hirewise.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Request body for ROLE_ASSIGN (HR_ADMIN only) - assigns a role to a user.
 * A user may hold multiple roles at once (e.g. both RECRUITER and
 * INTERVIEWER).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignRoleRequestDto {

    @NotBlank(message = "{validation.role_assignment.role_code.required}")
    private String roleCode;

    /** Defaults to now if left blank. */
    private Instant validFrom;

    /** Leave blank for no expiration. */
    private Instant validTo;
}
