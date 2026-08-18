package com.hirewise.be.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** ROLE_ASSIGN (chi HR_ADMIN) - gan 1 role cho 1 user. 1 user co the giu
 * nhieu role dong thoi (vd vua RECRUITER vua INTERVIEWER). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AssignRoleRequestDto {

    @NotBlank(message = "{validation.role_assignment.role_code.required}")
    private String roleCode;

    /** Mac dinh = ngay lap tuc neu de trong. */
    private Instant validFrom;

    /** De trong = vo thoi han. */
    private Instant validTo;
}
