package com.hirewise.be.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for USER_CREATE (HR_ADMIN only).
 * <p>
 * No password is supplied here - {@code UserAdminService#create} creates
 * the account in status {@code INVITED} with no usable password yet, and
 * enqueues the EM-01 activation email (see {@code event.OutboxEvent}) carrying a
 * one-time link the new hire uses to set their own password, per UC-02
 * normal flow.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequestDto {

    @NotBlank(message = "{validation.user.email.required}")
    @Email(message = "{validation.user.email.invalid}")
    @Size(max = 255, message = "{validation.user.email.size}")
    private String email;

    @Size(max = 255, message = "{validation.user.full_name.size}")
    private String fullName;

    /** Primary organizational department (BR-RBAC-05) - not an access scope. */
    private Long departmentId;
}
