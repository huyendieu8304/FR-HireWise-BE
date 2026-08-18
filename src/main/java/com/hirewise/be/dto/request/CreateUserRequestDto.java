package com.hirewise.be.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * USER_CREATE (chi HR_ADMIN). Khac ban truoc: KHONG con nhan keycloakId tu
 * client - UserAdminService#create tu goi KeycloakAdminClient#createUser de
 * tao that tai khoan Keycloak (username/email = email nay) va lay ve
 * keycloakId, dung UC-02 (HR Admin chi nhap thong tin, he thong tu tao tai
 * khoan + gui email kich hoat EM-01), khong con gia dinh tai khoan Keycloak
 * da ton tai san nua.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequestDto {

    @NotBlank
    @Email
    @Size(max = 255)
    private String email;

    @Size(max = 255)
    private String fullName;

    /** Phong ban to chuc chinh (BR-RBAC-05) - khong phai pham vi truy cap. */
    private Long departmentId;
}
