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
 * Unlike the previous version, this no longer accepts a keycloakId from the
 * client - {@code UserAdminService#create} calls
 * {@code KeycloakAdminClient#createUser} itself to actually create the
 * Keycloak account (username/email = this email) and retrieve the
 * keycloakId, per UC-02 (the HR Admin only enters the info; the system
 * creates the account and sends the activation email EM-01). It no longer
 * assumes a Keycloak account already exists beforehand.
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
